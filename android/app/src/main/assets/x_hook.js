// x.com sayfasının yaptığı GraphQL isteklerinin yanıtlarını uygulamaya iletir.
// Sayfa betiklerinden önce (document start) yüklenir; istekleri X'in kendi kodu atar.
(function () {
  if (window.__smsHooked) return;
  window.__smsHooked = true;
  var bridge = window.SMSBridge;
  if (!bridge) return;

  function wanted(url) {
    return typeof url === 'string' && url.indexOf('/graphql/') >= 0 && /User|Tweet|Timeline/.test(url);
  }

  var originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || String(input);
      return originalFetch.apply(this, arguments).then(function (response) {
        if (wanted(url)) {
          try {
            response.clone().text().then(function (text) {
              bridge.onResponse(url, response.status, text);
            });
          } catch (e) {}
        }
        return response;
      });
    };
  }

  var open = XMLHttpRequest.prototype.open;
  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__smsUrl = String(url);
    return open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    var xhr = this;
    if (wanted(xhr.__smsUrl)) {
      xhr.addEventListener('load', function () {
        try {
          var body = xhr.responseType === '' || xhr.responseType === 'text' ? xhr.responseText : JSON.stringify(xhr.response);
          bridge.onResponse(xhr.__smsUrl, xhr.status, body);
        } catch (e) {}
      });
    }
    return send.apply(this, arguments);
  };

  // Sunucu tarafında render edilen sayfalarda ilk veriler satır içi betiklerle gelir
  // ve betikler çalıştıktan sonra kendilerini siler; eklendikleri anda yakalanır.
  new MutationObserver(function (mutations) {
    mutations.forEach(function (m) {
      m.addedNodes.forEach(function (node) {
        if (node.tagName === 'SCRIPT' && node.textContent && node.textContent.indexOf('Result.Ok') >= 0) {
          try { bridge.onEmbedded(node.textContent); } catch (e) {}
        }
      });
    });
  }).observe(document, { childList: true, subtree: true });
})();
