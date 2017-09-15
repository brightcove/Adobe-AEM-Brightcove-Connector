package com.coresecure.brightcove.wrapper.utils;

import com.coresecure.brightcove.wrapper.sling.ServiceUtil;
import org.apache.sling.api.SlingHttpServletRequest;

/**
 * Created by pablo.kropilnicki on 6/28/17.
 */
public class AccountUtil
{
    public static String getSelectedAccount(SlingHttpServletRequest req)
    {
        String accountParam = req.getParameter("account_id");
        String selectedaccount = accountParam != null && !accountParam.isEmpty() ? accountParam : ServiceUtil.getAccountFromCookie(req);
        return selectedaccount;
    }


}
