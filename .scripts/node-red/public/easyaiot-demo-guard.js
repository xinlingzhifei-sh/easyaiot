/**
 * yFeiEye：演示规则链页签只读 — 禁用 Deploy，并提示不可改删
 * 由 settings.js editorTheme.page.scripts 注入
 */
(function () {
  var DEMO_IDS = {
    easyaiot_demo_telemetry: 1,
    easyaiot_demo_alert: 1,
    easyaiot_demo_bridge: 1,
    easyaiot_demo_vision: 1,
  };

  function isDemo(id) {
    return !!(id && DEMO_IDS[id]);
  }

  function setDeployLocked(locked) {
    var btn = document.getElementById('red-ui-header-button-deploy');
    if (!btn) return;
    if (locked) {
      btn.classList.add('disabled');
      btn.style.pointerEvents = 'none';
      btn.style.opacity = '0.45';
      btn.title = 'yFeiEye 演示规则链只读，禁止部署修改';
    } else {
      btn.classList.remove('disabled');
      btn.style.pointerEvents = '';
      btn.style.opacity = '';
      btn.title = '';
    }
  }

  function refresh() {
    try {
      if (typeof RED === 'undefined' || !RED.workspaces) return;
      var id = RED.workspaces.active();
      setDeployLocked(isDemo(id));
    } catch (e) {}
  }

  function boot() {
    refresh();
    try {
      if (RED && RED.events) {
        RED.events.on('workspace:change', refresh);
        RED.events.on('view:selection-changed', refresh);
      }
    } catch (e) {}
    setInterval(refresh, 1500);
  }

  if (document.readyState === 'complete') {
    setTimeout(boot, 800);
  } else {
    window.addEventListener('load', function () {
      setTimeout(boot, 800);
    });
  }
})();
