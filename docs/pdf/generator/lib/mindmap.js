'use strict';
/* Mind map diagram (kept in its own module so layout coordinates are easy to adjust). */
module.exports = function mindmap(doc, x, y, W, C, helpers) {
  const { box, text } = helpers;
  const cw = 130, cx = x + W / 2, cy = y + 190;
  const L = x + 2, R = x + W - cw - 2, Mid = x + (W - cw) / 2;
  const nodes = [
    ['Control plane', L + 10, y + 0, '#dfeefc', 'registry - versions\npolicy workflow\nconsumers/keys - audit'],
    ['Gateway / data plane', R - 10, y + 0, '#dfeefc', 'route table - auth\nlimits - proxy\nbreaker - bulkhead'],
    ['Testing + performance', L, y + 95, '#eaf1fb', 'unit - IT - e2e\nLoadProbe - k6 scripts'],
    ['Security', R, y + 95, '#fde8e8', 'JWT/OIDC - BOLA\nSSRF - threat model'],
    ['Data (MySQL, Redis,\nFlyway)', L, y + 172, '#e8f6ee', 'schema - indexes\nplans - retention'],
    ['Observability', R, y + 172, '#f1e9fb', 'metrics - traces - logs\ncardinality - incidents'],
    ['AI-assisted engineering', L + 10, y + 300, '#eef0f3', 'specs - skills\nmulti-agent - reviews'],
    ['Resilience', R, y + 250, '#fff1e0', 'timeouts - retries\nfail-static - drills'],
    ['Delivery (Docker, K8s,\nHelm, CI/CD)', Mid, y + 320, '#e8f6ee', ''],
  ];
  nodes.forEach(([, nx, ny]) => {
    doc.save();
    doc.moveTo(cx, cy).lineTo(nx + cw / 2, ny + 18).strokeColor('#b4bcc8').lineWidth(0.8).stroke();
    doc.restore();
  });
  box(doc, cx - 70, cy - 20, 140, 40, 'API Management +\nObservability Platform', { fill: '#0b5cad', stroke: '#0b5cad', color: '#ffffff', bold: true, size: 9.5 });
  nodes.forEach(([t, nx, ny, fill, leaf]) => {
    box(doc, nx, ny, cw, 36, t, { fill, bold: true, size: 8.2 });
    if (leaf) {
      leaf.split('\n').forEach((ln, i) => text(doc, nx, ny + 39 + i * 8.5, ln, { size: 6.8, w: cw, align: 'center' }));
    }
  });
};
