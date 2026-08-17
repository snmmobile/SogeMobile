package com.example.viewonlybrowser;

import org.json.JSONObject;

/** JavaScript installed into trusted Sogebanking pages to detect and protect the dashboard. */
final class ViewOnlyScripts {
    private ViewOnlyScripts() {
    }

    static String targetDetector(String detectedUrl, boolean readonlyEnabled, boolean functionBlockingEnabled) {
        return "(function(){"
                + blockerInstaller()
                + "if(window.__mobileDashboardDetectorInstalled)return;window.__mobileDashboardDetectorInstalled=true;"
                + "var observer;"
                + "function textOf(selector){var e=document.querySelector(selector);"
                + "return e?(e.textContent||'').replace(/\\s+/g,' ').trim().toLocaleLowerCase():'';}"
                + "function hasText(selector,text){return textOf(selector).indexOf(text)!==-1;}"
                // Stable page chrome only. No customer, account, or balance text is inspected.
                + "function isTarget(){return "
                + "hasText('lng[key=\"accounts.depositAccounts\"]','comptes de dépôt')"
                + "&&hasText('.logout','se déconnecter')"
                + "&&hasText('lng[key=\"newShareApplication.welcome\"]','bienvenue');}"
                + "function check(){if(!isTarget())return;if(observer)observer.disconnect();"
                // Apply enabled protections synchronously before notifying Android.
                + installCalls(readonlyEnabled, functionBlockingEnabled)
                + "window.location.href='" + detectedUrl + "';}"
                + "observer=new MutationObserver(check);"
                + "observer.observe(document.documentElement,{childList:true,subtree:true,characterData:true});"
                + "check();"
                + "})();";
    }

    static String interactionBlocker(boolean readonlyEnabled, boolean functionBlockingEnabled) {
        return "(function(){" + blockerInstaller()
                + installCalls(readonlyEnabled, functionBlockingEnabled)
                + "})();";
    }

    static String temporaryAccountDisplayOverride(String accountId, String balanceText) {
        if (accountId == null || !accountId.matches("[0-9]{6,20}")
                || balanceText == null || balanceText.trim().isEmpty()) {
            return "";
        }

        String id = JSONObject.quote(accountId);
        String balance = JSONObject.quote(balanceText.trim());
        return "(function(){"
                + "var accountId=" + id + ",balance=" + balance + ";"
                + "function apply(){"
                + "var row=document.querySelector('tr.account_item[account_id=\"'+accountId+'\"]');"
                + "if(!row)return;"
                + "var top=row.querySelector('table>tbody>tr:first-child');if(!top)return;"
                + "[1,2].forEach(function(i){var cell=top.children[i];if(!cell)return;"
                + "var value=cell.querySelector('div');if(!value)return;"
                + "value.textContent=balance;value.classList.add('sogemobile-temp-balance');});"
                + "var label=row.querySelector('.info_acc_top');"
                + "if(label&&!label.querySelector('.sogemobile-temp-badge')){"
                + "var badge=document.createElement('span');badge.className='sogemobile-temp-badge';"
                + "badge.textContent='TEMP';badge.title='Temporary local display override';"
                + "label.appendChild(badge);}}"
                + "if(!document.getElementById('sogemobile-temp-style')){"
                + "var style=document.createElement('style');style.id='sogemobile-temp-style';"
                + "style.textContent='.sogemobile-temp-balance{display:inline-block!important;"
                + "margin-bottom:0!important;padding:5px 9px;border-radius:7px;"
                + "background:linear-gradient(135deg,#f4f8ff,#e7f0ff);"
                + "box-shadow:inset 0 0 0 1px rgba(3,75,158,.16);"
                + "font-size:15px!important;font-weight:600!important;letter-spacing:.1px;}"
                + ".sogemobile-temp-badge{display:inline-block;margin-left:8px;padding:2px 5px;"
                + "border-radius:999px;background:#fff3cd;color:#7a5600;font:700 9px/1.4 sans-serif;"
                + "letter-spacing:.5px;vertical-align:middle;}';"
                + "(document.head||document.documentElement).appendChild(style);}"
                + "apply();new MutationObserver(apply).observe(document.documentElement,"
                + "{childList:true,subtree:true});})();";
    }

    private static String installCalls(boolean readonlyEnabled, boolean functionBlockingEnabled) {
        return (readonlyEnabled ? "window.__installReadOnlyBlocker();" : "")
                + (functionBlockingEnabled ? "window.__installFunctionBlocker();" : "");
    }

    private static String blockerInstaller() {
        return "window.__installReadOnlyBlocker=window.__installReadOnlyBlocker||function(){"
                + "if(window.__readOnlyInstalled)return;window.__readOnlyInstalled=true;"
                + "var s=document.createElement('style');"
                + "s.textContent='a,area,button,input,select,textarea,[role=button],[onclick],"
                + "[contenteditable],[tabindex],summary,label,video,audio,iframe"
                + "{pointer-events:none!important;cursor:default!important}';"
                + "(document.head||document.documentElement).appendChild(s);"
                + "['click','auxclick','dblclick','submit','contextmenu'].forEach(function(e){"
                + "document.addEventListener(e,function(x){x.preventDefault();x.stopImmediatePropagation();},true);});"
                + "document.addEventListener('keydown',function(e){if(e.key==='Enter'||e.key===' '){"
                + "e.preventDefault();e.stopImmediatePropagation();}},true);"
                + "document.querySelectorAll('button,input,select,textarea').forEach(function(e){e.disabled=true;});"
                + "try{history.pushState=function(){};history.replaceState=function(){};}catch(e){}"
                + "};"
                + "window.__installFunctionBlocker=window.__installFunctionBlocker||function(){"
                + "if(window.__functionBlockerInstalled)return;window.__functionBlockerInstalled=true;"
                + "var s=document.createElement('style');"
                + "s.textContent='.transfer_options,.account_details"
                + "{display:none!important;pointer-events:none!important}';"
                + "(document.head||document.documentElement).appendChild(s);"
                + "};";
    }
}
