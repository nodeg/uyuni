/*
 * Copyright (c) 2021 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package com.redhat.rhn.manager.system;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import com.redhat.rhn.GlobalInstanceHolder;
import com.redhat.rhn.common.hibernate.LookupException;
import com.redhat.rhn.common.validator.ValidatorException;
import com.redhat.rhn.common.validator.ValidatorResult;
import com.redhat.rhn.domain.action.ActionChain;
import com.redhat.rhn.domain.action.ActionChainFactory;
import com.redhat.rhn.domain.server.AnsibleFactory;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.domain.server.ansible.AnsiblePath;
import com.redhat.rhn.domain.server.ansible.InventoryPath;
import com.redhat.rhn.domain.server.ansible.PlaybookPath;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.xmlrpc.UnsupportedOperationException;
import com.redhat.rhn.manager.BaseManager;
import com.redhat.rhn.manager.action.ActionChainManager;
import com.redhat.rhn.manager.action.ActionManager;
import com.redhat.rhn.manager.entitlement.EntitlementManager;
import com.redhat.rhn.manager.system.entitling.SystemEntitlementManager;
import com.redhat.rhn.taskomatic.TaskomaticApiException;

import com.suse.manager.webui.services.iface.SaltApi;
import com.suse.manager.webui.services.pillar.MinionPillarManager;
import com.suse.manager.webui.utils.salt.custom.AnsiblePlaybookSlsResult;
import com.suse.salt.netapi.calls.LocalCall;
import com.suse.salt.netapi.datatypes.target.MinionList;
import com.suse.salt.netapi.utils.Xor;

import com.google.gson.reflect.TypeToken;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AnsibleManager extends BaseManager {

    private static Logger log = LogManager.getLogger(AnsibleManager.class);
    private final SaltApi saltApi;

    /**
     * Constructor
     *
     * @param saltApiIn the Salt API
     */
    public AnsibleManager(SaltApi saltApiIn) {
        saltApi = saltApiIn;
    }

    /**
     * Lookup ansible path by id
     *
     * @param id the id
     * @param user the user doing the lookup
     * @return AnsiblePath or empty if not found
     * @throws LookupException if the user does not have permissions to the minion
     */
    public static Optional<AnsiblePath> lookupAnsiblePathById(long id, User user) {
        Optional<AnsiblePath> ansiblePath = AnsibleFactory.lookupAnsiblePathById(id);
        ansiblePath.ifPresent(p -> SystemManager.ensureAvailableToUser(user, p.getMinionServer().getId()));
        return ansiblePath;
    }

    /**
     * List ansible paths by minion
     *
     * @param minionServerId the minion id
     * @param user the user performing the action
     * @return the list of AnsiblePaths of minion
     * @throws LookupException if the user does not have permissions to the minion
     */
    public static List<AnsiblePath> listAnsiblePaths(long minionServerId, User user) {
        lookupAnsibleControlNode(minionServerId, user);
        return AnsibleFactory.listAnsiblePaths(minionServerId);
    }

    /**
     * List playbook paths by minion
     *
     * @param minionId the minion id
     * @param user the user performing the action
     * @return the list of PlaybookPaths of minion
     * @throws LookupException if the user does not have permissions to the minion
     */
    public static List<PlaybookPath> listAnsiblePlaybookPaths(long minionId, User user) {
        SystemManager.ensureAvailableToUser(user, minionId);
        return AnsibleFactory.listAnsiblePlaybookPaths(minionId);
    }

    /**
     * List inventory paths by minion
     *
     * @param minionId the minion id
     * @param user the user performing the action
     * @return the list of InventoryPaths of minion
     * @throws LookupException if the user does not have permissions to the minion
     */
    public static List<InventoryPath> listAnsibleInventoryPaths(long minionId, User user) {
        SystemManager.ensureAvailableToUser(user, minionId);
        return AnsibleFactory.listAnsibleInventoryPaths(minionId);
    }

    /**
     * Create and save a new ansible path
     *
     * @param typeLabel the type label
     * @param minionServerId minion server id
     * @param path the path
     * @param user the user performing the action
     * @return the created and saved AnsiblePath
     * @throws LookupException if the user does not have permissions or server not found
     * @throws ValidatorException if the validation fails
     */
    public AnsiblePath createAnsiblePath(String typeLabel, long minionServerId, String path, User user) {
        MinionServer minionServer = lookupAnsibleControlNode(minionServerId, user);
        return createAnsiblePath(typeLabel, minionServer, path);
    }

    /**
     * Create and save a new ansible path
     *
     * @param typeLabel the type label
     * @param minionServer the minion server
     * @param path the path
     * @return the created and saved AnsiblePath
     * @throws LookupException if the user does not have permissions or server not found
     * @throws ValidatorException if the validation fails
     */
    public AnsiblePath createAnsiblePath(String typeLabel, MinionServer minionServer, String path) {
        validateAnsiblePath(path, of(typeLabel), empty(), minionServer.getId());

        AnsiblePath ansiblePath;
        AnsiblePath.Type type = AnsiblePath.Type.fromLabel(typeLabel);
        switch (type) {
            case INVENTORY:
                ansiblePath = new InventoryPath();
                break;
            case PLAYBOOK:
                ansiblePath = new PlaybookPath();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported type " + type);
        }

        ansiblePath.setMinionServer(minionServer);
        ansiblePath.setPath(Path.of(path));
        ansiblePath = AnsibleFactory.saveAnsiblePath(ansiblePath);

        if (type == AnsiblePath.Type.INVENTORY) {
            // Schedule inventory refresh
            try {
                ActionManager.scheduleInventoryRefresh(ansiblePath.getMinionServer(), ansiblePath.getPath().toString());
            }
            catch (TaskomaticApiException e) {
                log.error("Could not schedule Ansible inventory refresh for minion: {}",
                        ansiblePath.getMinionServer().getMinionId(), e);
            }

            // Refresh inotify beacon
            MinionPillarManager.INSTANCE.generatePillar(minionServer, false,
                    MinionPillarManager.PillarSubset.GENERAL);
            saltApi.refreshPillar(new MinionList(minionServer.getMinionId()));
        }

        return ansiblePath;
    }

    /**
     * Update an existing ansible path
     *
     * @param existingPathId the path id
     * @param newPath the new path
     * @param user the user performing the action
     * @return the updated path
     * @throws LookupException if the user does not have permissions or existing path not found
     * @throws ValidatorException if the validation fails
     */
    public AnsiblePath updateAnsiblePath(long existingPathId, String newPath, User user) {
        AnsiblePath existing = lookupAnsiblePathById(existingPathId, user)
                .orElseThrow(() -> new LookupException("Ansible path id " + existingPathId + " not found."));
        if (existing.getPath().toString().equals(newPath)) {
            // nothing to update
            return existing;
        }
        validateAnsiblePath(newPath, empty(), of(existingPathId), existing.getMinionServer().getId());
        existing.setPath(Path.of(newPath));

        if (existing.getEntityType() == AnsiblePath.Type.INVENTORY) {
            // Remove ansible managed systems from changed inventory and schedule inventory refresh
            try {
                handleInventoryRefresh((InventoryPath) existing, new HashSet<>());
                AnsibleFactory.saveAnsiblePath(existing);
                ActionManager.scheduleInventoryRefresh(existing.getMinionServer(), existing.getPath().toString());
            }
            catch (TaskomaticApiException e) {
                log.error("Could not schedule Ansible inventory refresh for minion: {}",
                        existing.getMinionServer().getMinionId(), e);
            }

            // Refresh inotify beacon
            MinionPillarManager.INSTANCE.generatePillar(existing.getMinionServer(), false,
                    MinionPillarManager.PillarSubset.GENERAL);
            saltApi.refreshPillar(new MinionList(existing.getMinionServer().getMinionId()));
        }
        else {
            AnsibleFactory.saveAnsiblePath(existing);
        }

        return existing;
    }

    private static void validateAnsiblePath(String path, Optional<String> typeLabel, Optional<Long> pathId,
            long minionServerId) {
        ValidatorResult result = new ValidatorResult();

        typeLabel.ifPresent(lbl -> {
            try {
                AnsiblePath.Type.fromLabel(lbl);
            }
            catch (IllegalArgumentException e) {
                result.addFieldError("type", "ansible.invalid_path_type");
            }
        });

        if (path == null || path.isBlank()) {
            result.addFieldError("path", "ansible.invalid_path");
        }
        try {
            Path.of(path);
        }
        catch (InvalidPathException e) {
            result.addFieldError("path", "ansible.invalid_path");
        }

        Path actualPath = Path.of(path);

        if (!actualPath.isAbsolute()) {
            result.addFieldError("path", "ansible.invalid_path");
        }

        Optional<AnsiblePath> duplicatePath = AnsibleFactory
                .lookupAnsiblePathByPathAndMinion(actualPath, minionServerId);
        duplicatePath.ifPresent(dup -> { // an ansible path with same minion and path exists
            pathId.ifPresentOrElse(p -> { // if we're updating, the IDs must be same
                        if (!p.equals(dup.getId())) {
                            result.addFieldError("path", "ansible.duplicate_path");
                        }
                    },
                    () -> { // creating is illegal in this case
                        result.addFieldError("path", "ansible.duplicate_path");
                    });
        });

        if (result.hasErrors()) {
            throw new ValidatorException(result);
        }
    }

    /**
     *
     * Remove ansible path
     *
     * @param pathId the id of {@link AnsiblePath}
     * @param user the user performing the action
     * @throws LookupException if the user does not have permissions or if entity does not exist
     */
    public void removeAnsiblePath(long pathId, User user) {
        AnsiblePath path = lookupAnsiblePathById(pathId, user)
                .orElseThrow(() -> new LookupException("Ansible path id " + pathId + " not found."));

        if (path.getEntityType() == AnsiblePath.Type.INVENTORY) {
            // Remove entitlement from managed servers
            handleInventoryRefresh((InventoryPath) path, new HashSet<>());
            AnsibleFactory.removeAnsiblePath(path);

            // Refresh inotify beacon
            MinionPillarManager.INSTANCE.generatePillar(path.getMinionServer(), false,
                    MinionPillarManager.PillarSubset.GENERAL);
            saltApi.refreshPillar(new MinionList(path.getMinionServer().getMinionId()));
        }
        else {
            AnsibleFactory.removeAnsiblePath(path);
        }
    }

    /**
     * Remove all ansible paths for given minion server
     *
     * @param minionServer the minion server
     */
    public void removeAnsiblePaths(MinionServer minionServer) {
        List<AnsiblePath> paths = AnsibleFactory.listAnsiblePaths(minionServer.getId());
        Set<Server> systemsToRemove = new HashSet<>();
        paths.forEach(p -> {
            if (p.getEntityType() == AnsiblePath.Type.INVENTORY) {
                systemsToRemove.addAll(((InventoryPath) p).getInventoryServers());
            }
            AnsibleFactory.removeAnsiblePath(p);
        });
        if (!systemsToRemove.isEmpty()) {
            List<Server> ansibleManagedSystems = AnsibleFactory.listAnsibleInventoryServersExcludingControlNode(
                    minionServer.getId());
            systemsToRemove.removeIf(ansibleManagedSystems::contains);
            updateAnsibleManagedSystems(new HashSet<>(), systemsToRemove);
        }
        MinionPillarManager.INSTANCE.generatePillar(minionServer, false, MinionPillarManager.PillarSubset.GENERAL);
        saltApi.refreshPillar(new MinionList(minionServer.getMinionId()));
    }

    /**
     * Fetch playbook contents of based on given {@link PlaybookPath} id and relative path inside it.
     *
     * For instance, if the {@link PlaybookPath} has path attribute equal to "/root/playbooks", calling this method with
     * playbook relative path equal to "lamp_ha/site.yml" will result in fetching "/root/playbooks/lamp_ha/site.yml"
     * file from the control node assigned to this {@link PlaybookPath}.
     *
     * <strong>Uses a synchronous salt call for fetching.</strong>
     *
     * @param pathId the path id
     * @param playbookRelPathStr the relative path to the playbook file
     * @param user the user
     * @return the playbook contents or empty if minion did not respond
     * @throws LookupException when playbook path not found or accessible
     * @throws IllegalArgumentException when the specified relative path is absolute
     */
    public Optional<String> fetchPlaybookContents(long pathId, String playbookRelPathStr, User user) {
        AnsiblePath path = lookupAnsiblePathById(pathId, user)
                .orElseThrow(() -> new LookupException("Ansible playbook path id " + pathId + " not found."));
        if (!(path instanceof PlaybookPath)) {
            throw new LookupException("Path id " + pathId + " is not a playbook path id ");
        }

        Path playbookRelPath = Path.of(playbookRelPathStr);
        if (playbookRelPath.isAbsolute()) {
            throw new IllegalArgumentException("Path must be relative: " + playbookRelPathStr);
        }

        Path localPath = path.getPath().resolve(playbookRelPath);
        LocalCall<Xor<Boolean, String>> call = new LocalCall<>(
                "cp.get_file_str",
                of(List.of(localPath.toString())),
                empty(),
                new TypeToken<>() { }
        );

        Optional<Xor<Boolean, String>> result = saltApi.callSync(call, path.getMinionServer().getMinionId());
        return result.map(r -> r.right().orElseThrow(() -> new IllegalStateException("no result")));
    }

    /**
     * Schedules playbook execution
     *
     * @param playbookPath playbook path
     * @param inventoryPath inventory path
     * @param controlNodeId control node id
     * @param testMode true if the playbook should be executed as test mode
     * @param flushCache true if --flush-cache flag is to be set
     * @param extraVars the extra vars
     * @param earliestOccurrence earliestOccurrence
     * @param actionChainLabel the action chain label
     * @param user the user
     * @return the scheduled action id
     * @throws TaskomaticApiException if taskomatic is down
     * @throws IllegalArgumentException if playbook path is empty
     */
    public static Long schedulePlaybook(String playbookPath, String inventoryPath, long controlNodeId, boolean testMode,
            boolean flushCache, String extraVars, Date earliestOccurrence, Optional<String> actionChainLabel, User user)
            throws TaskomaticApiException {
        if (StringUtils.isBlank(playbookPath)) {
            throw new IllegalArgumentException("Playbook path cannot be empty.");
        }

        Server controlNode = lookupAnsibleControlNode(controlNodeId, user);
        ActionChain actionChain = actionChainLabel
                .filter(StringUtils::isNotEmpty)
                .map(l -> ActionChainFactory.getOrCreateActionChain(l, user))
                .orElse(null);

        return ActionChainManager.scheduleExecutePlaybook(user, controlNode.getId(), playbookPath,
                inventoryPath, actionChain, earliestOccurrence, testMode, flushCache, extraVars).getId();
    }

    /**
     * Discover playbooks in given {@link PlaybookPath} id
     *
     * <strong>Uses a synchronous salt call for fetching.</strong>
     *
     * The result has following structure:
     * Map of playbook path string -> Map of playbook name -> Playbook information as {@link AnsiblePlaybookSlsResult}.
     *
     * @param pathId the {@link PlaybookPath} id
     * @param user the user
     * @return the structure containing the playbooks information or empty optional if minion does not respond
     * @throws LookupException if the user does not have permissions to the minion associated with the path
     * @throws IllegalStateException if there is an error during the salt call
     */
    public Optional<Map<String, Map<String, AnsiblePlaybookSlsResult>>> discoverPlaybooks(long pathId,
            User user) {
        AnsiblePath path = lookupAnsiblePathById(pathId, user)
                .orElseThrow(() -> new LookupException(String.format("Path id %d not found", pathId)));

        if (!(path instanceof PlaybookPath)) {
            throw new IllegalArgumentException(String.format("Path id %d not a Playbook path", path.getId()));
        }

        LocalCall<Xor<String, Map<String, Map<String, AnsiblePlaybookSlsResult>>>> discoverCall = new LocalCall<>(
                "ansible.discover_playbooks",
                of(List.of(path.getPath().toString())),
                empty(),
                new TypeToken<>() { });

        return saltApi.callSync(discoverCall, path.getMinionServer().getMinionId())
                .map(res -> res.fold(
                        error -> {
                            throw new IllegalStateException(error);
                        },
                        success -> success));
    }

    /**
     * Introspect inventory in given {@link InventoryPath}
     * Uses a synchronous salt call for this discovery.
     *
     * <strong>Uses a synchronous salt call for fetching.</strong>
     *
     * @param pathId the {@link InventoryPath} id
     * @param user the user
     * @return the structure with the inventory contents
     * @throws LookupException if the user does not have permissions to the minion associated with the path
     * @throws IllegalStateException if there is an error during the salt call
     */
    public Optional<Map<String, Map<String, Object>>> introspectInventory(long pathId, User user) {
        AnsiblePath path = lookupAnsiblePathById(pathId, user)
                .orElseThrow(() -> new LookupException(String.format("Path id %d not found", pathId)));

        if (!(path instanceof InventoryPath)) {
            throw new IllegalArgumentException(String.format("Path %d not an Inventory path", path.getId()));
        }

        LocalCall<Xor<String, Map<String, Map<String, Object>>>> call = new LocalCall<>(
                "ansible.targets",
                empty(),
                of(Map.of("inventory", path.getPath().toString())),
                new TypeToken<>() { });

        return saltApi.callSync(call, path.getMinionServer().getMinionId())
                .map(res -> res.fold(
                        error -> {
                            throw new IllegalStateException(error);
                        },
                        success -> success));
    }

    /**
     * Parse Ansible Inventory content to look for host names
     *
     * @param inventoryMap the Ansible Inventory content
     * @return the Set of hostnames
     */
    public static Set<String> parseInventoryAndGetHostnames(Map<String, Map<String, Object>> inventoryMap) {
        HashSet<String> hostnames = new HashSet<>();

        for (Map.Entry<String, Map<String, Object>> entry : inventoryMap.entrySet()) {
            String ansibleGroupName = entry.getKey();
            if (!ansibleGroupName.equals("_meta")) {
                @SuppressWarnings("unchecked")
                List<String> hostList = (List<String>)entry.getValue().get("hosts");
                if (CollectionUtils.isNotEmpty(hostList)) {
                    hostnames.addAll(hostList);
                }
            }
        }

        return hostnames;
    }

    /**
     * Generate the inotify beacon for the given ansible control node based on
     * the {@link InventoryPath} related to it.
     *
     * @param minion the minion server
     * @return the inotify beacon configuration
     */
    public static List<Map<String, Object>> generateBeacon(MinionServer minion) {
        List<Map<String, Object>> beacon = new ArrayList<>();
        List<InventoryPath> inventories = AnsibleFactory.listAnsibleInventoryPaths(minion.getId());

        if (!inventories.isEmpty()) {
            Map<String, Object> files = new HashMap<>();
            inventories.forEach(i -> files.put(i.getPath().toString(), Map.of("mask", List.of("modify"))));
            beacon.add(Map.of("files", files));
            beacon.add(Map.of("interval", 5));
            beacon.add(Map.of("disable_during_state_run", true));
        }

        return beacon;
    }

    /**
     * Handle add or remove ansible_managed entitlement from systems
     *
     * @param inventory the inventory
     * @param systemsToAdd the systems to add
     */
    public static void handleInventoryRefresh(InventoryPath inventory, Set<Server> systemsToAdd) {
        Set<Server> systemsToRemove = inventory.getInventoryServers();
        systemsToRemove.removeAll(systemsToAdd);

        // Only remove entitlement from servers if they no longer appear in the list of ansible managed systems
        List<Server> ansibleManagedSystems = AnsibleFactory.listAnsibleInventoryServersExcludingPath(inventory);
        systemsToRemove.removeIf(ansibleManagedSystems::contains);

        updateAnsibleManagedSystems(systemsToAdd, systemsToRemove);

        inventory.setInventoryServers(systemsToAdd);
    }

    /**
     * Handle add or remove ansible_managed entitlement from systems
     *
     * @param systemsToAdd the servers to add
     * @param systemsToRemove the servers to remove
     */
    public static void updateAnsibleManagedSystems(Set<Server> systemsToAdd, Set<Server> systemsToRemove) {
        SystemEntitlementManager systemEntitlementManager = GlobalInstanceHolder.SYSTEM_ENTITLEMENT_MANAGER;

        // Add entitlement to servers
        systemsToAdd.forEach(s -> {
            if (!s.hasEntitlement(EntitlementManager.ANSIBLE_MANAGED) &&
                    systemEntitlementManager.canEntitleServer(s, EntitlementManager.ANSIBLE_MANAGED)) {
                systemEntitlementManager.addEntitlementToServer(s, EntitlementManager.ANSIBLE_MANAGED);
            }
        });
        // Remove entitlement from servers
        systemsToRemove.forEach(s ->
                systemEntitlementManager.removeServerEntitlement(s, EntitlementManager.ANSIBLE_MANAGED)
        );
    }

    /**
     * Create default playbook and inventory paths for given minion server
     *
     * @param minionServer the minion server
     */
    public void createDefaultPaths(MinionServer minionServer) {
        String inventory = "/etc/ansible/hosts";
        String playbook = "/etc/ansible/playbooks";
        try {
            createAnsiblePath(AnsiblePath.Type.INVENTORY.getLabel(), minionServer, inventory);
        }
        catch (ValidatorException e) {
            log.info(String.format("Inventory path: '%s' already exists. Skipping...", inventory));
        }
        try {
            createAnsiblePath(AnsiblePath.Type.PLAYBOOK.getLabel(), minionServer, playbook);
        }
        catch (ValidatorException e) {
            log.info(String.format("Playbook path: '%s' already exists. Skipping...", playbook));
        }
    }

    private static MinionServer lookupAnsibleControlNode(long systemId, User user) {
        Server controlNode = SystemManager.lookupByIdAndUser(systemId, user);
        if (controlNode == null) {
            throw new LookupException("Ansible control node " + systemId + " not found/accessible.");
        }
        if (!controlNode.hasAnsibleControlNodeEntitlement()) {
            throw new LookupException(controlNode.getHostname() + " is not an Ansible control node");
        }

        return controlNode.asMinionServer().orElseThrow(() -> new LookupException(controlNode + " is not a minion"));
    }
}
