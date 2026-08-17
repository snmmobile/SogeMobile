package com.example.viewonlybrowser;

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
                + "s.textContent='.account_more,.transfer_options,.account_details,"
                + "a[href^=\"javascript:load_account(\"],a[href^=\"javascript:load_content(\"]"
                + "{display:none!important;pointer-events:none!important}';"
                + "(document.head||document.documentElement).appendChild(s);"
                + "try{window.load_account=function(){return false;};}catch(e){}"
                + "var blockedLoadContent=function(){return false;};"
                + "try{window.load_content=blockedLoadContent;}catch(e){}"
                + "try{Object.defineProperty(window,'load_content',{configurable:false,"
                + "get:function(){return blockedLoadContent;},set:function(){}});}catch(e){}"
                + "};";
    }
}
