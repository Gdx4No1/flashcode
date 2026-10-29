(() => {
  const parentOrigin = document.currentScript?.dataset.parentOrigin;
  const appId = location.pathname.split('/')[1];
  if (!parentOrigin || window.parent === window) return;
  let enabled = false;
  let highlighted = null;
  let previousOutline = '';
  const clear = () => {
    if (highlighted) highlighted.style.outline = previousOutline;
    highlighted = null;
  };
  const selectorFor = (element) => {
    const parts = [];
    for (let node = element; node && node !== document.documentElement; node = node.parentElement) {
      if (node.id) { parts.unshift('#' + CSS.escape(node.id)); break; }
      const tag = node.tagName.toLowerCase();
      const peers = node.parentElement ? [...node.parentElement.children].filter(item => item.tagName === node.tagName) : [];
      parts.unshift(tag + (peers.length > 1 ? ':nth-of-type(' + (peers.indexOf(node) + 1) + ')' : ''));
    }
    return parts.join(' > ');
  };
  window.addEventListener('message', event => {
    if (event.source !== parent || event.origin !== parentOrigin) return;
    if (String(event.data?.appId) !== appId) return;
    if (event.data?.type === 'flashcode:selection-clear') { clear(); return; }
    if (event.data?.type !== 'flashcode:selection-mode') return;
    enabled = event.data.enabled === true;
    if (!enabled) clear();
  });
  document.addEventListener('click', event => {
    if (!enabled || !(event.target instanceof Element)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    const selector = selectorFor(event.target);
    if (!selector || selector.length > 2000) return;
    clear(); highlighted = event.target; previousOutline = highlighted.style.outline;
    highlighted.style.outline = '2px solid #7467f0';
    parent.postMessage({type:'flashcode:element-selected', appId, selector}, parentOrigin);
  }, true);
})();
