/*
 * noshorts.js — Quita los Shorts de m.youtube.com.
 *
 * La app lo inyecta al empezar y al terminar de cargar cada página, y en cada cambio
 * de URL. Antes de este archivo define window.__noShortsCss con el contenido de
 * noshorts.css. Es idempotente: si ya corrió en esta página, no hace nada.
 *
 * Qué hace:
 *   1. Mete el CSS de noshorts.css en un <style>.
 *   2. Redirige /shorts/ID → /watch?v=ID, incluso cuando YouTube cambia la URL sin
 *      recargar (history.pushState/replaceState, porque es una SPA).
 *   3. Con un MutationObserver, oculta lo que el CSS no puede detectar: contenedores
 *      que enlazan a /shorts/ y estantes/pestañas/chips cuyo texto es "Shorts".
 *
 * PARA ACTUALIZAR cuando YouTube cambie su HTML, tocá las listas de CONFIG.
 */
(function () {
  'use strict';
  if (window.__noShortsLoaded) return;
  window.__noShortsLoaded = true;

  // ─────────────────────────────── CONFIG ───────────────────────────────

  var CONFIG = {
    // Si un enlace a /shorts/ está dentro de uno de estos, se oculta el contenedor
    // más cercano. Ordenados de más específico a más general.
    itemContainers: [
      'ytm-shorts-lockup-view-model-v2',
      'ytm-shorts-lockup-view-model',
      'ytm-reel-item-renderer',
      'ytm-rich-item-renderer',
      'ytm-video-with-context-renderer',
      'ytm-compact-video-renderer',
      'ytm-video-card-renderer',
      'ytm-playlist-video-renderer',
      'ytm-media-item',
      'ytm-pivot-bar-item-renderer'
    ],

    // Secciones/estantes que se ocultan si su título es "Shorts" o si todo lo que
    // contienen son Shorts.
    shelfContainers: [
      'ytm-rich-section-renderer',
      'ytm-reel-shelf-renderer',
      'ytm-shelf-renderer',
      'grid-shelf-view-model',
      'ytm-item-section-renderer'
    ],

    // Dónde buscar el título de un estante.
    shelfTitles: [
      '.reel-shelf-title',
      '.shelf-header-layout-title',
      'h2',
      '.yt-shelf-header-layout__title',
      '[role="heading"]'
    ],

    // Pestañas y "chips" que se ocultan si su texto es exactamente "Shorts":
    // barra inferior, pestañas de canal, filtros de búsqueda y de inicio.
    labeledItems: [
      'ytm-pivot-bar-item-renderer',
      'yt-tab-shape',
      '.single-column-browse-results-tab-bar a',
      'ytm-chip-cloud-chip-renderer',
      'yt-chip-cloud-chip-renderer',
      'chip-shape'
    ],

    // Textos que identifican a los Shorts (en minúscula).
    labels: ['shorts']
  };

  // ───────────────────────────── fin CONFIG ─────────────────────────────

  var HIDDEN = 'data-noshorts-hidden';

  // 1. Redirección /shorts/ID → /watch?v=ID
  function shortsToWatch(url) {
    try {
      var u = new URL(url, location.href);
      var m = u.pathname.match(/^\/shorts\/([A-Za-z0-9_-]+)/);
      if (!m) return null;
      return u.origin + '/watch?v=' + m[1];
    } catch (e) {
      return null;
    }
  }

  function checkLocation() {
    var target = shortsToWatch(location.href);
    if (target) location.replace(target);
  }

  function wrapHistory(name) {
    var original = history[name];
    history[name] = function (state, title, url) {
      if (url != null) {
        var target = shortsToWatch(String(url));
        if (target) {
          location.replace(target);
          return;
        }
      }
      return original.apply(this, arguments);
    };
  }
  wrapHistory('pushState');
  wrapHistory('replaceState');
  window.addEventListener('popstate', checkLocation);
  // Evento propio de YouTube al empezar una navegación interna.
  window.addEventListener('yt-navigate-start', checkLocation);
  window.addEventListener('state-navigatestart', checkLocation);

  // Clic en un enlace a un Short (si llegara a verse): ir al reproductor normal.
  document.addEventListener('click', function (ev) {
    var a = ev.target && ev.target.closest && ev.target.closest('a[href*="/shorts/"]');
    if (!a) return;
    var target = shortsToWatch(a.href);
    if (!target) return;
    ev.preventDefault();
    ev.stopImmediatePropagation();
    location.assign(target);
  }, true);

  checkLocation();

  // 2. CSS
  function injectCss() {
    if (document.getElementById('noshorts-style')) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var style = document.createElement('style');
    style.id = 'noshorts-style';
    style.textContent = window.__noShortsCss || '';
    parent.appendChild(style);
  }

  // 3. Limpieza dinámica
  function hide(el) {
    if (el && !el.hasAttribute(HIDDEN)) el.setAttribute(HIDDEN, '');
  }

  function isShortsLabel(text) {
    return CONFIG.labels.indexOf((text || '').trim().toLowerCase()) !== -1;
  }

  var itemSelector = CONFIG.itemContainers.join(',');
  var shelfSelector = CONFIG.shelfContainers.join(',');

  function clean() {
    injectCss();
    var root = document.body;
    if (!root) return;

    // Enlaces a /shorts/: ocultar el item que los contiene.
    var links = root.querySelectorAll('a[href*="/shorts/"]');
    for (var i = 0; i < links.length; i++) {
      var item = links[i].closest(itemSelector);
      hide(item || links[i]);
    }

    // Estantes titulados "Shorts".
    var shelves = root.querySelectorAll(shelfSelector);
    for (var j = 0; j < shelves.length; j++) {
      var shelf = shelves[j];
      if (shelf.hasAttribute(HIDDEN)) continue;
      for (var k = 0; k < CONFIG.shelfTitles.length; k++) {
        var title = shelf.querySelector(CONFIG.shelfTitles[k]);
        if (title && isShortsLabel(title.textContent)) {
          hide(shelf);
          break;
        }
      }
    }

    // Pestañas y chips con texto "Shorts".
    var labeled = root.querySelectorAll(CONFIG.labeledItems.join(','));
    for (var n = 0; n < labeled.length; n++) {
      if (isShortsLabel(labeled[n].textContent)) hide(labeled[n]);
    }
  }

  var scheduled = false;
  function scheduleClean() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(function () {
      scheduled = false;
      clean();
    });
  }

  function start() {
    clean();
    new MutationObserver(scheduleClean).observe(document.documentElement, {
      childList: true,
      subtree: true
    });
  }

  injectCss();
  if (document.documentElement) {
    start();
  } else {
    document.addEventListener('DOMContentLoaded', start);
  }
  window.addEventListener('yt-navigate-finish', scheduleClean);
})();
