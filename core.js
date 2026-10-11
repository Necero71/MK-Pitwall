/* MK PITWALL core logic. Pure functions, no DOM. */
(function (root) {
  'use strict';

  /* Parameter list mirrors the F1 setup menu sections.
     No min/max is enforced: the game's own menu is the source of truth.
     `def` values are just a neutral starting baseline. */
  var PARAMS = [
    { k: 'fw',    g: 'Aerodynamics',        l: 'Front Wing',            step: 1,    dec: 0, def: 25 },
    { k: 'rw',    g: 'Aerodynamics',        l: 'Rear Wing',             step: 1,    dec: 0, def: 25 },
    { k: 'dOn',   g: 'Transmission',        l: 'Diff On Throttle',      step: 1,    dec: 0, def: 70, unit: '%' },
    { k: 'dOff',  g: 'Transmission',        l: 'Diff Off Throttle',     step: 1,    dec: 0, def: 50, unit: '%' },
    { k: 'fCam',  g: 'Suspension Geometry', l: 'Front Camber',          step: 0.1,  dec: 2, def: -3.0, unit: '°' },
    { k: 'rCam',  g: 'Suspension Geometry', l: 'Rear Camber',           step: 0.1,  dec: 2, def: -1.5, unit: '°' },
    { k: 'fToe',  g: 'Suspension Geometry', l: 'Front Toe',             step: 0.01, dec: 2, def: 0.05, unit: '°' },
    { k: 'rToe',  g: 'Suspension Geometry', l: 'Rear Toe',              step: 0.01, dec: 2, def: 0.20, unit: '°' },
    { k: 'fSus',  g: 'Suspension',          l: 'Front Suspension',      step: 1,    dec: 0, def: 25 },
    { k: 'rSus',  g: 'Suspension',          l: 'Rear Suspension',       step: 1,    dec: 0, def: 25 },
    { k: 'fArb',  g: 'Suspension',          l: 'Front Anti-Roll Bar',   step: 1,    dec: 0, def: 10 },
    { k: 'rArb',  g: 'Suspension',          l: 'Rear Anti-Roll Bar',    step: 1,    dec: 0, def: 10 },
    { k: 'fRh',   g: 'Suspension',          l: 'Front Ride Height',     step: 1,    dec: 0, def: 20 },
    { k: 'rRh',   g: 'Suspension',          l: 'Rear Ride Height',      step: 1,    dec: 0, def: 35 },
    { k: 'bPr',   g: 'Brakes',              l: 'Brake Pressure',        step: 1,    dec: 0, def: 100, unit: '%' },
    { k: 'bBias', g: 'Brakes',              l: 'Front Brake Bias',      step: 1,    dec: 0, def: 55, unit: '%' },
    { k: 'tFL',   g: 'Tyre Pressures',      l: 'Front Left',            step: 0.1,  dec: 1, def: 25.0, unit: 'psi' },
    { k: 'tFR',   g: 'Tyre Pressures',      l: 'Front Right',           step: 0.1,  dec: 1, def: 25.0, unit: 'psi' },
    { k: 'tRL',   g: 'Tyre Pressures',      l: 'Rear Left',             step: 0.1,  dec: 1, def: 21.0, unit: 'psi' },
    { k: 'tRR',   g: 'Tyre Pressures',      l: 'Rear Right',            step: 0.1,  dec: 1, def: 21.0, unit: 'psi' }
  ];
  var BY_KEY = {};
  PARAMS.forEach(function (p) { BY_KEY[p.k] = p; });

  var WEATHER = ['dry', 'wet'];

  function round(v, dec) {
    var f = Math.pow(10, dec);
    return Math.round((v + (v < 0 ? -1e-9 : 1e-9)) * f) / f;
  }

  function fmtVal(p, v) {
    if (typeof p === 'string') p = BY_KEY[p];
    if (typeof v !== 'number' || !isFinite(v)) return '--';
    return v.toFixed(p.dec);
  }

  function stepValue(key, v, dir) {
    var p = BY_KEY[key];
    var base = (typeof v === 'number' && isFinite(v)) ? v : p.def;
    return round(base + dir * p.step, p.dec);
  }

  function defaults() {
    var o = {};
    PARAMS.forEach(function (p) { o[p.k] = p.def; });
    return o;
  }

  function uid() {
    return 's' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  }

  function blank() {
    return { id: null, name: '', track: '', weather: 'dry', lap: '', notes: '', updated: 0, p: defaults() };
  }

  function str(v, max) {
    return (typeof v === 'string' ? v : '').slice(0, max);
  }

  /** Make any object safe: known keys only, numbers only, trimmed strings. */
  function clean(raw) {
    var s = blank();
    if (!raw || typeof raw !== 'object') return s;
    s.id = typeof raw.id === 'string' && raw.id ? raw.id.slice(0, 40) : null;
    s.name = str(raw.name, 60);
    s.track = str(raw.track, 40);
    s.weather = WEATHER.indexOf(raw.weather) >= 0 ? raw.weather : 'dry';
    s.lap = str(raw.lap, 16);
    s.notes = str(raw.notes, 1000);
    s.updated = typeof raw.updated === 'number' && isFinite(raw.updated) ? raw.updated : 0;
    var src = raw.p && typeof raw.p === 'object' ? raw.p : {};
    PARAMS.forEach(function (p) {
      var v = src[p.k];
      if (typeof v === 'number' && isFinite(v)) s.p[p.k] = round(v, p.dec);
    });
    return s;
  }

  /** Differences between two setups' parameters. Returns every param with changed flag. */
  function diff(a, b) {
    return PARAMS.map(function (p) {
      var av = a.p[p.k], bv = b.p[p.k];
      var d = round(bv - av, p.dec);
      return { k: p.k, g: p.g, l: p.l, dec: p.dec, unit: p.unit || '', a: av, b: bv, d: d, changed: d !== 0 };
    });
  }

  function fmtDelta(d, dec) {
    if (d === 0) return '0';
    return (d > 0 ? '+' : '') + d.toFixed(dec);
  }

  /** Plain-text setup sheet for sharing. */
  function toText(s) {
    var lines = [];
    var title = [s.name, s.track, s.weather ? s.weather.toUpperCase() : ''].filter(Boolean).join(' | ');
    if (title) lines.push(title);
    if (s.lap) lines.push('Best lap: ' + s.lap);
    var g = '';
    PARAMS.forEach(function (p) {
      if (p.g !== g) { g = p.g; lines.push('', g.toUpperCase()); }
      lines.push(p.l + ': ' + fmtVal(p, s.p[p.k]) + (p.unit === '%' ? '%' : ''));
    });
    if (s.notes) lines.push('', 'Notes: ' + s.notes);
    return lines.join('\n');
  }

  /**
   * Merge imported JSON into an existing array of setups.
   * Same id: keep whichever was updated last. Returns {list, added, updated, skipped}.
   */
  function mergeImport(existing, json) {
    var incoming = json && Array.isArray(json.setups) ? json.setups : (Array.isArray(json) ? json : null);
    if (!incoming) return null;
    var list = existing.slice();
    var added = 0, updated = 0, skipped = 0;
    incoming.slice(0, 500).forEach(function (raw) {
      if (!raw || typeof raw !== 'object') { skipped++; return; }
      var s = clean(raw);
      if (!s.id) s.id = uid();
      if (!s.updated) s.updated = Date.now();
      var i = -1;
      for (var n = 0; n < list.length; n++) { if (list[n].id === s.id) { i = n; break; } }
      if (i < 0) { list.push(s); added++; }
      else if (s.updated > list[i].updated) { list[i] = s; updated++; }
      else skipped++;
    });
    return { list: list, added: added, updated: updated, skipped: skipped };
  }

  /* Rules-of-thumb troubleshooting. d: 'u' raise the value, 'd' lower it.
     These are general setup behaviour, not F1 26 measurements. */
  var FIX = [
    { g: 'Turn-in and entry', items: [
      { s: 'Understeer on entry (won’t turn in)', f: [
        ['Front Toe', 'u', 'sharper initial bite'],
        ['Diff Off Throttle', 'd', 'lets the rear rotate on lift and brake'],
        ['Front Anti-Roll Bar', 'd', 'more front grip'],
        ['Front Brake Bias', 'd', 'less nose-heavy under braking'],
        ['Front Wing', 'u', 'more front downforce']
      ] },
      { s: 'Oversteer on entry (rear steps out)', f: [
        ['Diff Off Throttle', 'u', 'more stability on lift and brake'],
        ['Front Brake Bias', 'u', 'keeps weight and braking on the front'],
        ['Rear Toe', 'u', 'more rear stability'],
        ['Rear Wing', 'u', 'more rear downforce'],
        ['Rear Anti-Roll Bar', 'd', 'more rear grip']
      ] },
      { s: 'Twitchy, nervous front end', f: [
        ['Front Toe', 'd', 'calmer turn-in'],
        ['Front Anti-Roll Bar', 'u', 'slower, steadier front response'],
        ['Rear Toe', 'u', 'plants the rear']
      ] }
    ] },
    { g: 'Mid-corner', items: [
      { s: 'Understeer mid-corner', f: [
        ['Front Wing', 'u', 'more front downforce'],
        ['Front Camber', 'd', 'more negative, more front grip'],
        ['Front Anti-Roll Bar', 'd', 'more front grip'],
        ['Rear Anti-Roll Bar', 'u', 'shifts balance forward'],
        ['Front Ride Height', 'd', 'a little lower, if you aren’t bottoming']
      ] },
      { s: 'Oversteer mid-corner', f: [
        ['Rear Wing', 'u', 'more rear downforce'],
        ['Rear Anti-Roll Bar', 'd', 'more rear grip'],
        ['Rear Camber', 'd', 'more negative, more rear grip'],
        ['Front Anti-Roll Bar', 'u', 'moves balance toward the rear']
      ] }
    ] },
    { g: 'Exit and traction', items: [
      { s: 'Understeer on power (exit)', f: [
        ['Diff On Throttle', 'd', 'lets the car rotate on exit'],
        ['Rear Wing', 'd', 'frees the rear up'],
        ['Rear Anti-Roll Bar', 'u', 'rotates the car'],
        ['Front Wing', 'u', 'more front grip']
      ] },
      { s: 'Oversteer or wheelspin on power', f: [
        ['Diff On Throttle', 'u', 'better traction out of corners'],
        ['Rear Wing', 'u', 'more rear grip'],
        ['Rear Anti-Roll Bar', 'd', 'more rear traction'],
        ['Rear Suspension', 'd', 'softer rear, more mechanical grip'],
        ['Rear Toe', 'u', 'more rear stability']
      ] }
    ] },
    { g: 'Braking', items: [
      { s: 'Front lock-ups', f: [
        ['Brake Pressure', 'd', 'less peak force'],
        ['Front Brake Bias', 'd', 'moves braking rearward']
      ] },
      { s: 'Rear lock-ups or unstable braking', f: [
        ['Front Brake Bias', 'u', 'moves braking forward'],
        ['Brake Pressure', 'd', 'less peak force'],
        ['Diff Off Throttle', 'u', 'more stability on lift']
      ] }
    ] },
    { g: 'Kerbs and bumps', items: [
      { s: 'Bouncy, bottoming out, unsettled on kerbs', f: [
        ['Front Ride Height', 'u', 'more clearance'],
        ['Rear Ride Height', 'u', 'more clearance'],
        ['Front Suspension', 'd', 'softer over kerbs'],
        ['Rear Suspension', 'd', 'softer over kerbs'],
        ['Front Anti-Roll Bar', 'd', 'less harsh'],
        ['Rear Anti-Roll Bar', 'd', 'less harsh']
      ] }
    ] },
    { g: 'Straights and tyres', items: [
      { s: 'Slow on the straights', f: [
        ['Rear Wing', 'd', 'less drag'],
        ['Front Wing', 'd', 'less drag, keep balance with the rear']
      ] },
      { s: 'Front tyres overheating or wearing', f: [
        ['Front Camber', 'u', 'less negative, spreads the load'],
        ['Front Toe', 'd', 'less scrub']
      ] },
      { s: 'Rear tyres overheating or wearing', f: [
        ['Diff On Throttle', 'd', 'less wheel load under power'],
        ['Rear Camber', 'u', 'less negative, spreads the load'],
        ['Rear Toe', 'd', 'less scrub']
      ] }
    ] }
  ];

  var api = {
    PARAMS: PARAMS, BY_KEY: BY_KEY, WEATHER: WEATHER, FIX: FIX,
    round: round, fmtVal: fmtVal, stepValue: stepValue, defaults: defaults,
    uid: uid, blank: blank, clean: clean, diff: diff, fmtDelta: fmtDelta,
    toText: toText, mergeImport: mergeImport
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PitCore = api;
})(typeof self !== 'undefined' ? self : this);
