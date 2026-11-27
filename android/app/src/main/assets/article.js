/* Reorganiza o HTML da wiki para leitura no celular. */
(function () {
  // compatibilidade com WebViews antigos (Android 5/6 sem atualização)
  if (!Element.prototype.matches) Element.prototype.matches = Element.prototype.webkitMatchesSelector;
  if (!Element.prototype.closest) Element.prototype.closest = function (sel) {
    var el = this; while (el && el.nodeType === 1) { if (el.matches(sel)) return el; el = el.parentElement; } return null;
  };
  if (!Element.prototype.remove) Element.prototype.remove = function () { if (this.parentNode) this.parentNode.removeChild(this); };
  var root = document.querySelector('.mw-parser-output') || document.body;
  var $$ = function (sel, el) { return Array.prototype.slice.call((el || document).querySelectorAll(sel)); };
  var txt = function (el) { return (el.textContent || '').replace(/\s+/g, ' ').trim(); };
  var T = window.__t || {};
  var tr = function (k, def, n) { var v = T[k] || def; return n == null ? v : v.replace('%d', n); };
  var mk = function (tag, cls, html) { var e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };

  // ---------- limpeza ----------
  $$('.mw-editsection, .toc, #toc, .noprint, .printfooter, .catlinks, .mw-empty-elt, #stub, table.alert, .reference, .mw-references-wrap').forEach(function (e) { e.remove(); });
  $$('p').forEach(function (p) { if (!txt(p) && !p.querySelector('img')) p.remove(); });
  $$('[style]').forEach(function (e) {
    if (e.tagName === 'IMG') return;
    e.style.width = ''; e.style.minWidth = ''; e.style.maxWidth = ''; e.style.float = ''; e.style.height = '';
  });

  // ---------- infobox ----------
  $$('#infoboxtable').forEach(function (t) {
    var card = mk('div', 'ibox');
    $$(':scope > tbody > tr', t).forEach(function (tr) {
      var cells = $$(':scope > td, :scope > th', tr);
      if (!cells.length) return;
      if (cells.length === 1) {
        var c = cells[0];
        var row = mk('div', c.id === 'infoboxheader' ? 'ibox-title' : (c.id === 'infoboxsection' ? 'ibox-sec' : 'ibox-full'));
        while (c.firstChild) row.appendChild(c.firstChild);
        if (!txt(row) && !row.querySelector('img')) return;
        card.appendChild(row);
      } else {
        var r = mk('div', 'ibox-row');
        var k = mk('div', 'ibox-k'), v = mk('div', 'ibox-v');
        while (cells[0].firstChild) k.appendChild(cells[0].firstChild);
        for (var i = 1; i < cells.length; i++) while (cells[i].firstChild) v.appendChild(cells[i].firstChild);
        if (!txt(v) && !v.querySelector('img')) return;
        r.appendChild(k); r.appendChild(v); card.appendChild(r);
      }
    });
    var holder = t.closest('#infoboxborder') || t;
    holder.parentNode.replaceChild(card, holder);
  });

  // ---------- tabelas de dados -> cartões ----------
  function grid(table) {
    // monta matriz respeitando rowspan/colspan
    var rows = $$(':scope > tbody > tr, :scope > thead > tr', table);
    var m = [], owner = [];
    rows.forEach(function (tr, r) {
      m[r] = m[r] || []; owner[r] = owner[r] || [];
      var c = 0;
      $$(':scope > td, :scope > th', tr).forEach(function (cell) {
        while (m[r][c]) c++;
        var cs = parseInt(cell.getAttribute('colspan') || '1', 10) || 1;
        var rs = parseInt(cell.getAttribute('rowspan') || '1', 10) || 1;
        for (var dr = 0; dr < rs; dr++) for (var dc = 0; dc < cs; dc++) {
          m[r + dr] = m[r + dr] || []; owner[r + dr] = owner[r + dr] || [];
          m[r + dr][c + dc] = cell; owner[r + dr][c + dc] = r;
        }
        c += cs;
      });
    });
    return { m: m, owner: owner, rows: rows };
  }

  function isHeaderRow(tr) {
    var cells = $$(':scope > td, :scope > th', tr);
    return cells.length > 0 && cells.every(function (c) { return c.tagName === 'TH'; });
  }

  // tabela de preços por qualidade -> "40–80 ouros"
  function compactPrices(el) {
    $$('table', el).forEach(function (t) {
      var rows = $$('tr', t);
      if (rows.length < 2) return;
      var nums = [];
      var all = rows.every(function (r) {
        var m = txt(r).match(/(\d[\d .]*)\s*ouros?/i);
        if (m) nums.push(m[1].replace(/\s|\./g, ''));
        return !!m;
      });
      if (!all || nums.length < 2) return;
      var icon = t.querySelector('img');
      var span = mk('span', 'price');
      if (icon) { var ic = icon.cloneNode(); ic.className = 'qicon'; span.appendChild(ic); }
      span.appendChild(document.createTextNode(' ' + fmt(nums[0]) + '–' + fmt(nums[nums.length - 1]) + ' ouros'));
      t.parentNode.replaceChild(span, t);
    });
  }
  function fmt(n) { return String(parseInt(n, 10)).replace(/\B(?=(\d{3})+(?!\d))/g, '.'); }

  function valueHtml(cells) {
    var seen = [], out = mk('div', 'v');
    cells.forEach(function (c) {
      if (!c || seen.indexOf(c) >= 0) return;
      seen.push(c);
      var w = mk('div', 'vp');
      while (c.firstChild) w.appendChild(c.firstChild);
      compactPrices(w);
      if (txt(w) || w.querySelector('img')) out.appendChild(w);
    });
    return out;
  }

  var EMPTY = /^(n\/?a|-|—|–|nenhum|nenhuma)?$/i;

  function convert(table) {
    if (table.closest('.ibox') || table.parentElement.closest('table')) return;
    if (!table.classList.contains('wikitable')) return;
    if (table.id === 'navbox' || table.classList.contains('navbox')) return;
    var g = grid(table);
    var ncols = Math.max.apply(null, g.m.map(function (r) { return r.length; }));
    if (ncols < 4) return; // tabelas estreitas ficam como tabela

    // cabeçalho: última linha só de <th> antes dos dados
    var h = -1, caption = '';
    for (var i = 0; i < g.rows.length; i++) {
      if (!isHeaderRow(g.rows[i])) break;
      var cells = $$(':scope > th', g.rows[i]);
      if (cells.length === 1 && (parseInt(cells[0].getAttribute('colspan') || '1', 10) > 1)) caption = txt(cells[0]);
      h = i;
    }
    if (h < 0) return;
    var labels = [];
    for (var c = 0; c < ncols; c++) labels[c] = g.m[h][c] ? txt(g.m[h][c]) : '';

    // colunas repetidas lado a lado (A|B|A|B) -> empilha em uma tabela estreita
    var per = labels.indexOf(labels[0], 1);
    if (labels[0] && per > 0 && ncols % per === 0 && labels.every(function (l, i) { return l === labels[i % per]; })) {
      var nt = mk('table', 'wikitable');
      var tb = mk('tbody'); nt.appendChild(tb);
      var hr = mk('tr'); labels.slice(0, per).forEach(function (l) { hr.appendChild(mk('th', '', l)); }); tb.appendChild(hr);
      for (var k = 0; k < ncols; k += per) {
        for (var rr = h + 1; rr < g.rows.length; rr++) {
          var tr = mk('tr'), any = false;
          for (var cc = k; cc < k + per; cc++) {
            var src = g.m[rr] && g.m[rr][cc]; var td = mk('td');
            if (src) { td.innerHTML = src.innerHTML; if (txt(src)) any = true; }
            tr.appendChild(td);
          }
          if (any) tb.appendChild(tr);
        }
      }
      table.parentNode.replaceChild(nt, table);
      if (per >= 4) convert(nt);
      return;
    }

    // registros = grupos de linhas ligados por rowspan
    var recs = [], cur = null;
    for (var r = h + 1; r < g.rows.length; r++) {
      var linked = false;
      for (var c2 = 0; c2 < ncols; c2++) if (g.owner[r] && g.owner[r][c2] !== undefined && g.owner[r][c2] < r && g.owner[r][c2] > h) { linked = true; break; }
      if (!linked || !cur) { cur = []; recs.push(cur); }
      cur.push(r);
    }
    if (!recs.length) return;

    var box = mk('div', 'cards');
    if (caption) box.appendChild(mk('div', 'cards-cap', caption));

    var ordinal = labels.filter(function (l) { return /^(n[ií]vel|est[aá]gio|dia|ano|level)\s*\d/i.test(l); }).length >= 3;
    if (ordinal || (recs.length <= 3 && recs.length < ncols)) {
      if (ordinal) recs = [Array.prototype.concat.apply([], recs)];
      // poucos registros e muitas colunas: um bloco por coluna
      recs.forEach(function (rec) {
        var tiles = mk('div', 'tiles');
        var used = [];
        for (var c = 0; c < ncols; c++) {
          var cells = rec.map(function (r) { return g.m[r] && g.m[r][c]; }).filter(function (x) { return x && used.indexOf(x) < 0; });
          if (!cells.length) continue;
          cells.forEach(function (x) { used.push(x); });
          var v = valueHtml(cells);
          if (!txt(v) && !v.querySelector('img')) continue;
          var tile = mk('div', 'tile' + (txt(v).length > 70 ? ' wide' : ''));
          if (labels[c]) tile.appendChild(mk('div', 'k', labels[c]));
          tile.appendChild(v);
          tiles.appendChild(tile);
        }
        box.appendChild(tiles);
      });
    } else {
      // um cartão por registro
      var iImg = -1, iName = -1, iDesc = -1;
      labels.forEach(function (l, i) {
        var L = l.toLowerCase();
        if (iImg < 0 && /^(imagem|ícone|icone|img)$/.test(L)) iImg = i;
        else if (iName < 0 && /^(nome|item|peixe|semente|sementes|receita|arma|animal|ferramenta|anel|chapéu|mineral|artefato|monstro|missão|conquista|livro)$/.test(L)) iName = i;
        else if (iDesc < 0 && /^descri/.test(L)) iDesc = i;
      });
      if (iImg < 0 && g.rows[h + 1]) {
        var first = g.m[h + 1][0];
        if (first && first.querySelector('img') && !txt(first)) iImg = 0;
      }
      if (iName < 0) iName = (iImg === 0 && ncols > 1) ? 1 : 0;

      var list = mk('div', 'clist');
      recs.forEach(function (rec) {
        var card = mk('div', 'card');
        var used = [];
        var take = function (c) {
          var cells = rec.map(function (r) { return g.m[r] && g.m[r][c]; }).filter(function (x) { return x && used.indexOf(x) < 0; });
          cells.forEach(function (x) { used.push(x); });
          return cells;
        };
        var head = mk('div', 'card-h');
        if (iImg >= 0) { var im = valueHtml(take(iImg)); im.className = 'card-img'; head.appendChild(im); }
        var nm = valueHtml(take(iName)); nm.className = 'card-name'; head.appendChild(nm);
        card.appendChild(head);
        if (iDesc >= 0) { var d = valueHtml(take(iDesc)); d.className = 'card-desc'; if (txt(d)) card.appendChild(d); }

        var fields = [];
        for (var c = 0; c < ncols; c++) {
          if (c === iImg || c === iName || c === iDesc) continue;
          var cells = take(c);
          if (!cells.length) continue;
          var v = valueHtml(cells);
          if (EMPTY.test(txt(v)) && !v.querySelector('img')) continue;
          var f = mk('div', 'f');
          f.appendChild(mk('div', 'k', labels[c] || ''));
          f.appendChild(v);
          fields.push(f);
        }
        // profissões/variações vão para o fim
        fields.sort(function (a, b) {
          var pa = /profiss|\(\+\d|\+\d+%/i.test(a.firstChild.textContent) ? 1 : 0;
          var pb = /profiss|\(\+\d|\+\d+%/i.test(b.firstChild.textContent) ? 1 : 0;
          return pa - pb;
        });
        var MAX = fields.length <= 5 ? 5 : 4;
        var fl = mk('div', 'fields');
        fields.slice(0, MAX).forEach(function (f) { fl.appendChild(f); });
        card.appendChild(fl);
        if (fields.length > MAX) {
          var more = mk('details', 'more');
          more.appendChild(mk('summary', '', tr('more', 'More details (%d)', fields.length - MAX)));
          var fl2 = mk('div', 'fields');
          fields.slice(MAX).forEach(function (f) { fl2.appendChild(f); });
          more.appendChild(fl2);
          card.appendChild(more);
        }
        list.appendChild(card);
      });
      box.appendChild(list);
      if (recs.length > 8) addFilter(box, list);
      if (recs.length > 12) addShowMore(list, 10);
    }
    table.parentNode.replaceChild(box, table);
  }

  function addFilter(box, list) {
    var inp = mk('input', 'filter');
    inp.type = 'search';
    inp.placeholder = tr('filter', 'Filter %d items…', list.children.length);
    inp.addEventListener('input', function () {
      var q = inp.value.trim().toLowerCase();
      list.classList.toggle('filtering', !!q);
      Array.prototype.forEach.call(list.children, function (c) {
        if (!c.classList.contains('card')) return;
        c.style.display = (!q || txt(c).toLowerCase().indexOf(q) >= 0) ? '' : 'none';
      });
    });
    box.insertBefore(inp, list);
  }

  function addShowMore(list, n) {
    var items = Array.prototype.slice.call(list.children);
    if (items.length <= n + 2) return;
    list.classList.add('clipped');
    items.forEach(function (it, i) { if (i >= n) it.classList.add('extra'); });
    var b = mk('button', 'showmore', tr('showAll', 'Show all (%d)', items.length));
    b.addEventListener('click', function () { list.classList.remove('clipped'); b.remove(); });
    list.parentNode.insertBefore(b, list.nextSibling);
  }

  // grades de links (ex.: menu de receitas) -> lista compacta
  $$('table').forEach(function (t) {
    if (t.parentElement.closest('table') || t.closest('.ibox')) return;
    if (t.querySelector('th')) return;
    var tds = $$('td', t);
    if (tds.length < 6) return;
    var ok = tds.every(function (td) { return !td.querySelector('table') && txt(td).length < 40 && (td.querySelector('a') || !txt(td)); });
    if (!ok) return;
    var ul = mk('ul', 'compact');
    tds.forEach(function (td) { if (!txt(td)) return; var li = mk('li'); while (td.firstChild) li.appendChild(td.firstChild); ul.appendChild(li); });
    t.parentNode.replaceChild(ul, t);
  });

  $$('table').forEach(function (t) { try { convert(t); } catch (e) { } });

  // navbox / tabelas recolhíveis
  $$('table#navbox, table.navbox').forEach(function (t) {
    if (t.closest('details')) return;
    var d = mk('details', 'navbox'); d.appendChild(mk('summary', '', tr('seeAlso', 'See also')));
    t.parentNode.insertBefore(d, t); d.appendChild(t);
  });
  $$('table.mw-collapsible').forEach(function (t) {
    if (t.parentElement.closest('table') || t.closest('details')) return;
    var th = t.querySelector('th');
    var d = mk('details', 'coll'); d.appendChild(mk('summary', '', th ? txt(th).substring(0, 60) : tr('show', 'Show')));
    if (th && th.parentElement && $$(':scope > th, :scope > td', th.parentElement).length === 1) th.parentElement.remove();
    t.parentNode.insertBefore(d, t); d.appendChild(t);
  });

  // tabelas restantes: rolagem horizontal; tabelas longas mostram só o começo
  $$('table').forEach(function (t) {
    if (t.parentElement.closest('table')) return;
    var trs = $$(':scope > tbody > tr', t);
    if (trs.length > 16 && !t.closest('details.navbox')) {
      t.classList.add('clipped');
      trs.forEach(function (tr, i) { if (i > 11) tr.classList.add('extra'); });
      var b = mk('button', 'showmore', tr('showTable', 'Show full table (%d rows)', trs.length));
      b.addEventListener('click', function () { t.classList.remove('clipped'); b.remove(); });
      t.parentNode.insertBefore(b, t.nextSibling);
    }
    if (t.parentElement.classList.contains('tw')) return;
    var w = mk('div', 'tw'); t.parentNode.insertBefore(w, t); w.appendChild(t);
  });

  // ---------- listas ----------
  $$('ul, ol').forEach(function (l) {
    if (l.classList.contains('gallery') || l.closest('.card') || l.closest('.tile') || l.closest('.ibox')) return;
    var items = $$(':scope > li', l);
    if (items.length < 6) return;
    var avg = items.reduce(function (s, li) { return s + txt(li).length; }, 0) / items.length;
    if (avg < 28 && !items.some(function (li) { return li.querySelector('ul,ol'); })) l.classList.add('compact');
    if (items.length > 14) addShowMore(l, 10);
  });

  // ---------- seções recolhíveis + resumo ----------
  var heads = $$(':scope > .mw-heading2, :scope > h2', root);
  if (heads.length) {
    var nav = mk('div', 'jump');
    heads.forEach(function (hd, i) {
      var h2 = hd.tagName === 'H2' ? hd : hd.querySelector('h2');
      if (!h2) return;
      var d = mk('details', 'sec');
      d.open = heads.length <= 2;
      var s = mk('summary');
      hd.parentNode.insertBefore(d, hd);
      s.appendChild(h2);
      d.appendChild(s);
      if (hd !== h2) hd.remove();
      var body = mk('div', 'sec-body');
      d.appendChild(body);
      while (d.nextSibling && !(d.nextSibling.nodeType === 1 && (d.nextSibling.classList.contains('mw-heading2') || d.nextSibling.tagName === 'H2'))) {
        body.appendChild(d.nextSibling);
      }
      var chip = mk('span', 'chip', txt(h2));
      chip.addEventListener('click', function () { d.open = true; d.scrollIntoView({ behavior: 'smooth' }); });
      nav.appendChild(chip);
    });

    // resumo: introdução curta + atalhos para as seções
    var firstSec = root.querySelector('details.sec');
    var lead = [], n = firstSec ? firstSec.previousSibling : null;
    var paras = [];
    var node = root.firstChild;
    while (node && node !== firstSec) { if (node.tagName === 'P') paras.push(node); node = node.nextSibling; }
    if (paras.length > 2) {
      var extra = paras.slice(2);
      var d2 = mk('details', 'leadmore'); d2.appendChild(mk('summary', '', tr('readIntro', 'Read full introduction')));
      paras[1].parentNode.insertBefore(d2, paras[1].nextSibling);
      extra.forEach(function (p) { d2.appendChild(p); });
    }
    if (heads.length > 2 && firstSec) {
      // abre a primeira seção se for curta
      if (txt(firstSec).length < 900) firstSec.open = true;
      var box = mk('div', 'jumpbox'); box.appendChild(mk('div', 'jump-t', tr('onPage', 'On this page')));
      box.appendChild(nav);
      root.insertBefore(box, firstSec);
    }
  }

  // abrir a seção certa ao ir para uma âncora
  window.__goto = function (id) {
    var e = document.getElementById(id);
    if (!e) return;
    var d = e.closest('details');
    while (d) { d.open = true; d = d.parentElement && d.parentElement.closest('details'); }
    e.scrollIntoView(); window.scrollBy(0, -8);
  };

  // ---------- ampliar imagens ----------
  (function () {
    function original(img) {
      var src = img.currentSrc || img.src || '';
      var ss = img.getAttribute('srcset');
      if (ss) { var parts = ss.split(','); src = parts[parts.length - 1].trim().split(' ')[0] || src; }
      // /images/thumb/a/ab/Nome.png/48px-Nome.png -> /images/a/ab/Nome.png
      var m = src.match(/^(.*\/images\/)thumb\/(.+?\/[^\/]+)\/[^\/]+$/);
      return m ? m[1] + m[2] : src;
    }
    function zoomable(img) {
      if (img.closest('.zoom-box')) return false;
      if ((img.naturalWidth || img.width) < 8) return false;
      var a = img.closest('a');
      // imagem que leva a outra página (ex.: ícone de item) continua abrindo a página
      if (a && !/(^|\s)(image|mw-file-description)(\s|$)/.test(a.className) && !/\/(File|Image|Arquivo|Ficheiro|Datei|Fichier|Archivo|Fájl|Dosya|Файл|ファイル|파일|文件)[:%]/i.test(a.getAttribute('href') || '')) return false;
      return true;
    }
    var box, pic, cap, scale = 1, tx = 0, ty = 0;
    function apply() { pic.style.transform = 'translate(' + tx + 'px,' + ty + 'px) scale(' + scale + ')'; }
    function close() { if (!box) return; box.remove(); box = null; document.body.style.overflow = ''; if (window.Zoom) Zoom.state(false); }
    window.__closeZoom = close;
    function open(img) {
      close();
      box = mk('div', 'zoom-box');
      var x = mk('button', 'zoom-x', '✕'); x.setAttribute('aria-label', tr('close', 'Close'));
      box.setAttribute('role', 'dialog'); box.setAttribute('aria-modal', 'true');
      pic = document.createElement('img'); pic.className = 'zoom-img'; pic.src = original(img);
      pic.onerror = function () { pic.onerror = null; pic.src = img.currentSrc || img.src; };
      var alt = img.getAttribute('alt') || ''; var fig = img.closest('.thumb, figure');
      var c = fig && fig.querySelector('.thumbcaption, figcaption'); alt = (c && txt(c)) || alt.replace(/\.(png|gif|jpe?g|webp)$/i, '');
      cap = mk('div', 'zoom-cap'); cap.textContent = alt; cap.setAttribute('aria-live', 'polite');
      pic.alt = alt;
      var hint = mk('div', 'zoom-hint', tr('zoomHint', 'Pinch or double-tap to zoom'));
      box.appendChild(pic); box.appendChild(x); if (alt) box.appendChild(cap); box.appendChild(hint);
      document.body.appendChild(box); document.body.style.overflow = 'hidden';
      scale = 1; tx = ty = 0;
      // pixel art pequena começa ampliada para caber bem na tela
      pic.onload = function () {
        var w = pic.naturalWidth, h = pic.naturalHeight, W = window.innerWidth * 0.86, H = window.innerHeight * 0.7;
        var f = Math.min(W / w, H / h); f = f > 1 ? Math.floor(f) : f;
        pic.style.width = Math.round(w * f) + 'px'; pic.style.height = Math.round(h * f) + 'px';
      };
      x.addEventListener('click', function (e) { e.stopPropagation(); close(); });
      box.addEventListener('click', function (e) { if (e.target === box) close(); });
      var last = 0, d0 = 0, s0 = 1, p0 = null, t0x = 0, t0y = 0, moved = false;
      function dist(t) { var dx = t[0].clientX - t[1].clientX, dy = t[0].clientY - t[1].clientY; return Math.sqrt(dx * dx + dy * dy); }
      box.addEventListener('touchstart', function (e) {
        moved = false;
        if (e.touches.length === 2) { d0 = dist(e.touches); s0 = scale; }
        else if (e.touches.length === 1) { p0 = { x: e.touches[0].clientX, y: e.touches[0].clientY }; t0x = tx; t0y = ty; }
      }, { passive: true });
      box.addEventListener('touchmove', function (e) {
        e.preventDefault(); moved = true;
        if (e.touches.length === 2 && d0) { scale = Math.max(1, Math.min(8, s0 * dist(e.touches) / d0)); apply(); }
        else if (e.touches.length === 1 && p0 && scale > 1) { tx = t0x + e.touches[0].clientX - p0.x; ty = t0y + e.touches[0].clientY - p0.y; apply(); }
      }, { passive: false });
      box.addEventListener('touchend', function (e) {
        if (e.touches.length) return;
        d0 = 0;
        if (scale <= 1) { tx = ty = 0; apply(); }
        if (moved) return;
        var now = Date.now();
        if (now - last < 300 && e.target !== x) { scale = scale > 1 ? 1 : 2.5; tx = ty = 0; apply(); last = 0; }
        else last = now;
      });
      if (window.Zoom) Zoom.state(true);
      try { x.focus(); } catch (e) {}
    }
    document.addEventListener('click', function (e) {
      var img = e.target && e.target.tagName === 'IMG' ? e.target : null;
      if (!img || !zoomable(img)) return;
      e.preventDefault(); e.stopPropagation();
      open(img);
    }, true);
  })();
})();

/* Páginas dos mods: nomes do jogo base no idioma da pessoa.
   O app manda um glossário (título em inglês -> título na wiki oficial do idioma), vindo dos links entre
   idiomas da wiki oficial. Só troca links que levam ao jogo base (stardewvalleywiki.com) e as estações;
   o texto do mod continua como está. */
(function () {
  var SEASONS = { Spring: 1, Summer: 1, Fall: 1, Winter: 1 };

  function target(a) {
    var h = a.getAttribute('href') || '';
    var m = h.match(/^https?:\/\/(?:www\.)?stardewvalleywiki\.com\/([^?#]+)/);
    if (m) {
      try { return { t: decodeURIComponent(m[1]).replace(/_/g, ' '), ext: true }; } catch (e) { return null; }
    }
    var l = h.match(/^(?:https?:\/\/[^/]+)?\/wiki\/([^?#]+)$/);
    if (l && SEASONS[l[1]]) return { t: l[1], ext: false };
    return null;
  }

  function swapText(node, from, to) {
    if (node.nodeType === 3) {
      if (node.nodeValue.trim().toLowerCase() === from.toLowerCase()) {
        node.nodeValue = node.nodeValue.replace(node.nodeValue.trim(), to);
        return true;
      }
      return false;
    }
    for (var c = node.firstChild; c; c = c.nextSibling) if (swapText(c, from, to)) return true;
    return false;
  }

  window.__gloss = function (map, base) {
    if (!map) return;
    var as = document.querySelectorAll('a[href]');
    for (var i = 0; i < as.length; i++) {
      var a = as[i];
      if (a.getAttribute('data-gloss')) continue;
      var g = target(a);
      if (!g) continue;
      var tr = map[g.t];
      if (!tr) continue;
      a.setAttribute('data-gloss', '1');
      if (a.textContent.trim().toLowerCase() === g.t.toLowerCase()) swapText(a, g.t, tr);
      if (g.ext && base) {
        a.setAttribute('href', base + '/' + encodeURIComponent(tr.replace(/ /g, '_')).replace(/%2F/g, '/').replace(/%3A/g, ':'));
      }
      a.setAttribute('title', g.t); // nome original em inglês
    }
  };

  if (window.__glossInit) window.__gloss(window.__glossInit.map, window.__glossInit.base);
})();

/* Páginas dos mods: tradução automática do texto (feita no aparelho pelo app).
   __mtCollect() devolve os textos da página; o app traduz e chama __mtApply(lista).
   A faixa no topo avisa que é tradução automática e alterna com o original. */
(function () {
  var T = window.__t || {};
  var nodes = [], orig = [], trans = null, showing = false;
  var fixed = [];   // tradução já conhecida (termo da wiki oficial), sem passar pelo tradutor automático
  var pending = []; // índices que vão para o tradutor automático
  var terms = {}, lower = {};
  /** Termos da wiki oficial no idioma (o app manda quando já tem; podem chegar depois de a página abrir). */
  window.__setTerms = function (m) {
    terms = m || {}; lower = {};
    for (var k in terms) lower[k.toLowerCase()] = terms[k];
  };
  window.__setTerms(window.__terms);

  /** Termo da wiki oficial para o texto (exato, sem maiúsculas ou no singular: "Loves" -> "Love"). */
  function term(t) {
    if (terms[t]) return terms[t];
    var l = t.toLowerCase();
    if (lower[l]) return lower[l];
    if (/s$/.test(l) && lower[l.slice(0, -1)]) return lower[l.slice(0, -1)];
    return null;
  }

  /** Nome de página do mod (link cujo texto é o próprio título: personagem, lugar, item do mod). */
  function properName(n) {
    for (var p = n.parentNode; p && p !== document.body; p = p.parentNode) {
      if (p.classList && (p.classList.contains('druid-title'))) return true;
      if (p.nodeName === 'A') {
        var t = (p.getAttribute('title') || '').trim();
        return t && t.toLowerCase() === n.nodeValue.trim().toLowerCase();
      }
    }
    return false;
  }
  var SKIP = { SCRIPT: 1, STYLE: 1, CODE: 1, PRE: 1, NOSCRIPT: 1 };

  function bar() {
    var b = document.getElementById('mt-bar');
    if (b) return b;
    b = document.createElement('div');
    b.id = 'mt-bar';
    b.className = 'mt-bar';
    b.setAttribute('role', 'status');
    var title = document.querySelector('.page-title');
    if (title && title.parentNode) title.parentNode.insertBefore(b, title); else document.body.insertBefore(b, document.body.firstChild);
    return b;
  }

  function render(state) {
    var b = bar();
    if (state === 'working') { b.innerHTML = '<span class="mt-ico">🌐</span> ' + (T.mtWorking || 'Translating…'); return; }
    b.innerHTML = '';
    var s = document.createElement('span');
    s.innerHTML = '<span class="mt-ico">🌐</span> ' + (showing ? (T.mtNotice || 'Automatic translation') : (T.mtOriginalShown || 'Original (English)'));
    var a = document.createElement('button');
    a.className = 'mt-toggle';
    a.textContent = showing ? (T.mtSeeOriginal || 'See original') : (T.mtSeeTranslation || 'Translate');
    a.onclick = function () { set(!showing); };
    b.appendChild(s); b.appendChild(document.createTextNode(' · ')); b.appendChild(a);
  }

  function set(on) {
    if (!trans) return;
    showing = on;
    for (var i = 0; i < nodes.length; i++) nodes[i].nodeValue = on ? trans[i] : orig[i];
    render('done');
  }

  window.__mtCollect = function () {
    nodes = []; orig = [];
    var root = document.body;
    var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        if (!/[A-Za-z]{2}/.test(n.nodeValue)) return NodeFilter.FILTER_REJECT;
        for (var p = n.parentNode; p && p !== root; p = p.parentNode) {
          if (SKIP[p.nodeName]) return NodeFilter.FILTER_REJECT;
          if (p.id === 'mt-bar' || (p.classList && (p.classList.contains('credit') || p.classList.contains('mod-badge') || p.classList.contains('page-title')))) return NodeFilter.FILTER_REJECT;
          if (p.getAttribute && p.getAttribute('data-gloss')) return NodeFilter.FILTER_REJECT; // nome já no idioma (glossário)
        }
        return NodeFilter.FILTER_ACCEPT;
      }
    });
    var n;
    fixed = []; pending = [];
    var out = [];
    while ((n = w.nextNode())) {
      var t = n.nodeValue.trim();
      var tm = term(t);
      if (!tm && properName(n)) continue; // nome próprio do mod: fica como está
      nodes.push(n); orig.push(n.nodeValue);
      fixed.push(tm);
      if (!tm) { pending.push(nodes.length - 1); out.push(n.nodeValue); }
    }
    if (nodes.length) render('working');
    if (!out.length && nodes.length) setTimeout(function () { window.__mtApply([]); }, 0);
    return out;
  };

  window.__mtApply = function (list) {
    if (!list || list.length !== pending.length) { var b = document.getElementById('mt-bar'); if (b) b.remove(); return; }
    var got = {};
    for (var j = 0; j < pending.length; j++) got[pending[j]] = list[j];
    trans = [];
    for (var i = 0; i < nodes.length; i++) {
      // mantém os espaços das pontas (o texto vizinho é outro nó)
      var o = orig[i], lead = o.match(/^\s*/)[0], tail = o.match(/\s*$/)[0];
      var v = fixed[i] != null ? fixed[i] : got[i];
      trans.push(lead + String(v == null ? o.trim() : v).trim() + tail);
    }
    set(true);
  };

  window.__mtFail = function () { var b = document.getElementById('mt-bar'); if (b) b.remove(); };
})();
