/* Reorganiza o HTML da wiki para leitura no celular. */
(function () {
  var root = document.querySelector('.mw-parser-output') || document.body;
  var $$ = function (sel, el) { return Array.prototype.slice.call((el || document).querySelectorAll(sel)); };
  var txt = function (el) { return (el.textContent || '').replace(/\s+/g, ' ').trim(); };
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
          more.appendChild(mk('summary', '', 'Mais detalhes (' + (fields.length - MAX) + ')'));
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
    inp.placeholder = 'Filtrar ' + list.children.length + ' itens…';
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
    var b = mk('button', 'showmore', 'Mostrar todos (' + items.length + ')');
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
    var d = mk('details', 'navbox'); d.appendChild(mk('summary', '', 'Ver também'));
    t.parentNode.insertBefore(d, t); d.appendChild(t);
  });
  $$('table.mw-collapsible').forEach(function (t) {
    if (t.parentElement.closest('table') || t.closest('details')) return;
    var th = t.querySelector('th');
    var d = mk('details', 'coll'); d.appendChild(mk('summary', '', th ? txt(th).substring(0, 60) : 'Mostrar'));
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
      var b = mk('button', 'showmore', 'Mostrar tabela inteira (' + trs.length + ' linhas)');
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
      var d2 = mk('details', 'leadmore'); d2.appendChild(mk('summary', '', 'Ler introdução completa'));
      paras[1].parentNode.insertBefore(d2, paras[1].nextSibling);
      extra.forEach(function (p) { d2.appendChild(p); });
    }
    if (heads.length > 2 && firstSec) {
      // abre a primeira seção se for curta
      if (txt(firstSec).length < 900) firstSec.open = true;
      var box = mk('div', 'jumpbox'); box.appendChild(mk('div', 'jump-t', 'Nesta página'));
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
})();
