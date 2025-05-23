/*
 * Copyright (c) 2009--2014 Red Hat, Inc.
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

package com.redhat.rhn.frontend.action.token.configuration;

import static com.redhat.rhn.manager.user.UserManager.ensureRoleBasedAccess;

import com.redhat.rhn.domain.access.Namespace;
import com.redhat.rhn.domain.config.ConfigChannel;
import com.redhat.rhn.domain.config.ConfigChannelListProcessor;
import com.redhat.rhn.domain.config.ConfigurationFactory;
import com.redhat.rhn.domain.token.ActivationKey;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.action.token.BaseListAction;
import com.redhat.rhn.frontend.dto.ConfigChannelDto;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.StrutsDelegate;
import com.redhat.rhn.frontend.taglibs.list.helper.ListSessionSetHelper;
import com.redhat.rhn.manager.configuration.ConfigurationManager;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @author paji
 * ConfigurationChannelsAction
 */
public class ListRemoveChannelsAction extends BaseListAction<ConfigChannelDto> {

    /** {@inheritDoc} */
    @Override
    public ActionForward handleDispatch(ListSessionSetHelper helper,
            ActionMapping mapping,
            ActionForm formIn, HttpServletRequest request,
            HttpServletResponse response) {
        RequestContext context = new RequestContext(request);
        User user = context.getCurrentUser();
        ensureRoleBasedAccess(user, "systems.activation_keys.config", Namespace.AccessMode.W);
        ActivationKey key = context.lookupAndBindActivationKey();
        ConfigChannelListProcessor proc = new ConfigChannelListProcessor();
        Set<String> set = helper.getSet();

        for (String id : set) {
            Long ccid = Long.valueOf(id);
            ConfigChannel cc = ConfigurationFactory.lookupConfigChannelById(ccid);
            proc.remove(key.getConfigChannelsFor(user), cc);
        }
        getStrutsDelegate().saveMessage(
                    "config_channels_to_unsubscribe.unsubscribe.success",
                        new String [] {String.valueOf(set.size())}, request);


        Map<String, Object> params = new HashMap<>();
        params.put(RequestContext.TOKEN_ID, key.getToken().getId().toString());
        StrutsDelegate strutsDelegate = getStrutsDelegate();
        return strutsDelegate.forwardParams
                        (mapping.findForward("success"), params);
    }


    /** {@inheritDoc} */
    @Override
    public List<ConfigChannelDto> getResult(RequestContext context) {
        ConfigurationManager cm = ConfigurationManager.getInstance();
        return cm.listGlobalChannelsForActivationKey(
                    context.lookupAndBindActivationKey(),
                    context.getCurrentUser());
    }
}
