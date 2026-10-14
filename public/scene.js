import * as THREE from './vendor/three.module.min.js';

const host = document.getElementById('visual-scene');
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' });
} catch {
  document.getElementById('scene-fallback').hidden = false;
}
if (renderer) initialize();

function initialize() {
  renderer.setPixelRatio(Math.min(devicePixelRatio, 1.6));
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.25;
  host.append(renderer.domElement);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(32, 1, .1, 100);
  camera.position.set(.1, .3, 7.9);
  camera.lookAt(0, -.12, 0);
  scene.add(new THREE.AmbientLight(0xabc8f2, 1.15));
  const key = new THREE.DirectionalLight(0xffffff, 3.7);
  key.position.set(-3, 5, 4); scene.add(key);
  const rim = new THREE.DirectionalLight(0x4f9cff, 4);
  rim.position.set(3, 2, -2); scene.add(rim);
  const fill = new THREE.DirectionalLight(0x91bdff, 1.6);
  fill.position.set(2, -1, 4); scene.add(fill);

  const brain = new THREE.Group();
  const material = new THREE.MeshPhysicalMaterial({ color: 0xaabed4, metalness: .32, roughness: .33, clearcoat: .7, clearcoatRoughness: .2, emissive: 0x0b2144, emissiveIntensity: .2 });
  for (const side of [-1, 1]) {
    const geometry = new THREE.SphereGeometry(1, 152, 112);
    const vertices = geometry.attributes.position;
    for (let i = 0; i < vertices.count; i++) {
      const x = vertices.getX(i), y = vertices.getY(i), z = vertices.getZ(i);
      const first = Math.abs(Math.sin(10 * y + 2.7 * Math.sin(4 * z) + 1.8 * Math.sin(5 * x)));
      const second = Math.abs(Math.sin(11 * z + 2.1 * Math.sin(6 * y) + 1.1 * Math.sin(4 * x)));
      const furrow = .105 * (1 - first) ** 5 + .07 * (1 - second) ** 6;
      const radius = 1 - furrow + .017 * Math.sin(4 * x + 3 * z);
      // Two folded hemispheres, separated by a deep central fissure.
      const inner = side * x < 0 ? .75 : 1;
      vertices.setXYZ(i, x * .73 * radius * inner + side * .5, y * .9 * radius + .08, z * 1.05 * radius);
    }
    geometry.computeVertexNormals();
    brain.add(new THREE.Mesh(geometry, material));
  }
  const cerebellum = new THREE.Mesh(new THREE.SphereGeometry(.33, 56, 40), material);
  cerebellum.scale.set(1.2, .65, .7); cerebellum.position.set(0, -.79, -.53); brain.add(cerebellum);
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(.11, .08, .34, 32), material);
  stem.position.set(0, -.98, -.23); stem.rotation.x = -.3; brain.add(stem);
  brain.rotation.set(.08, -.24, -.015); scene.add(brain);

  const face = new THREE.Group();
  const faceGeometry = new THREE.SphereGeometry(1, 120, 100);
  const faceVertices = faceGeometry.attributes.position;
  const gaussian = (x, y, cx, cy, spread) => Math.exp(-((x - cx) ** 2 + (y - cy) ** 2) / spread);
  for (let i = 0; i < faceVertices.count; i++) {
    const x = faceVertices.getX(i), y = faceVertices.getY(i), z = faceVertices.getZ(i);
    const front = Math.max(0, z) ** 5;
    const nose = .27 * gaussian(x, y, 0, .02, .025) + .08 * gaussian(x, y, 0, .27, .03);
    const sockets = .1 * (gaussian(x, y, -.3, .22, .018) + gaussian(x, y, .3, .22, .018));
    const cheeks = .045 * (gaussian(x, y, -.36, -.15, .08) + gaussian(x, y, .36, -.15, .08));
    const lips = .065 * gaussian(x, y, 0, -.28, .016);
    const lower = THREE.MathUtils.smoothstep(-y, .2, .85);
    faceVertices.setXYZ(i, x * .76 * (1 - lower * .16), y * 1.07, z * .65 + front * (nose - sockets + cheeks + lips));
  }
  faceGeometry.computeVertexNormals();
  face.add(new THREE.Mesh(faceGeometry, material));
  for (const side of [-1, 1]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(1, 24, 16), new THREE.MeshBasicMaterial({ color: 0x62baff }));
    eye.scale.set(.072, .012, .014); eye.position.set(side * .227, .233, .615); face.add(eye);
  }
  const mouth = new THREE.EllipseCurve(0, 0, .12, .014, 0, Math.PI, false, 0);
  const lip = new THREE.Line(new THREE.BufferGeometry().setFromPoints(mouth.getPoints(24)), new THREE.LineBasicMaterial({ color: 0x455e7c, transparent: true, opacity: .7 }));
  lip.position.set(0, -.299, .705); face.add(lip);
  face.visible = false; scene.add(face);

  // Independent neural network behind the brain: fine pathways, floating nodes.
  let seed = 91;
  const random = () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };
  const network = new THREE.Group();
  const nodes = [];
  for (let i = 0; i < 165; i++) {
    const angle = random() * Math.PI * 2;
    const radius = 1.4 + random() * 1.35;
    nodes.push(new THREE.Vector3(Math.cos(angle) * radius * 1.35, Math.sin(angle) * radius * .64, -1.1 - random() * 1.1));
  }
  const nodeGeometry = new THREE.BufferGeometry().setFromPoints(nodes);
  const nodeMaterial = new THREE.PointsMaterial({ color: 0x8bc5ff, size: .025, transparent: true, opacity: .65, sizeAttenuation: true });
  network.add(new THREE.Points(nodeGeometry, nodeMaterial));
  const segments = [];
  for (let i = 0; i < nodes.length; i++) {
    const nearby = nodes.map((point, index) => ({ index, distance: point.distanceTo(nodes[i]) })).filter(item => item.index > i && item.distance < .85).sort((a, b) => a.distance - b.distance).slice(0, 2);
    for (const neighbor of nearby) segments.push(nodes[i], nodes[neighbor.index]);
  }
  const lineMaterial = new THREE.LineBasicMaterial({ color: 0x5a8ac4, transparent: true, opacity: .18 });
  network.add(new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(segments), lineMaterial));
  const pulses = [];
  for (let i = 0; i < 10; i++) {
    const side = i % 2 ? 1 : -1;
    const y = (i / 10 - .5) * 2;
    const curve = new THREE.CubicBezierCurve3(new THREE.Vector3(side * .65, y * .35, -.4), new THREE.Vector3(side * 1.5, y * 1.2 + .5, -1.1), new THREE.Vector3(side * 2.4, y * .3 - .6, -1.5), new THREE.Vector3(side * 3.25, y, -1.8));
    network.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(curve.getPoints(75)), new THREE.LineBasicMaterial({ color: 0x85b8ff, opacity: .2, transparent: true })));
    const pulse = new THREE.Mesh(new THREE.SphereGeometry(.019, 8, 8), new THREE.MeshBasicMaterial({ color: 0xe4f3ff }));
    network.add(pulse); pulses.push({ mesh: pulse, curve, offset: random() });
  }
  scene.add(network);
  const internalNodes = [];
  for (let i = 0; i < 230; i++) {
    const vertex = brain.children[i % 2].geometry.attributes.position;
    const index = Math.floor(random() * vertex.count);
    internalNodes.push(new THREE.Vector3(vertex.getX(index), vertex.getY(index), vertex.getZ(index)).multiplyScalar(1.012));
  }
  const neuralSurface = new THREE.Points(new THREE.BufferGeometry().setFromPoints(internalNodes), new THREE.PointsMaterial({ color: 0x7fd5ff, size: .022, opacity: .9, transparent: true }));
  brain.add(neuralSurface);

  const base = new THREE.Group();
  const disc = new THREE.Mesh(new THREE.CylinderGeometry(1.48, 1.53, .045, 120), new THREE.MeshPhysicalMaterial({ color: 0x152945, metalness: .75, roughness: .28, transparent: true, opacity: .65 }));
  disc.position.y = -1.5; base.add(disc);
  for (const radius of [1.48, 1.61]) {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(radius, .006, 8, 120), new THREE.MeshBasicMaterial({ color: 0x6baaff, transparent: true, opacity: .48 }));
    ring.rotation.x = Math.PI / 2; ring.position.y = -1.47; base.add(ring);
  }
  scene.add(base);
  const state = { mode: '', theme: '', pointerX: 0, pointerY: 0 };
  host.addEventListener('pointermove', event => {
    const box = host.getBoundingClientRect();
    state.pointerX = (event.clientX - box.left) / box.width - .5;
    state.pointerY = (event.clientY - box.top) / box.height - .5;
  });
  host.addEventListener('pointerleave', () => { state.pointerX = state.pointerY = 0; });
  const resize = () => {
    const { width, height } = host.getBoundingClientRect();
    if (width <= 0 || height <= 0) return;
    renderer.setSize(width, height);
    camera.aspect = width / height;
    camera.position.z = width < 650 ? 9.6 : 7.9;
    camera.updateProjectionMatrix();
  };
  const observer = new ResizeObserver(resize); observer.observe(host); resize();
  const clock = new THREE.Clock();
  let frame;
  let stopped = false;
  function draw() {
    if (stopped) return;
    const seconds = reducedMotion ? 0 : clock.getElapsedTime();
    const mode = document.body.dataset.mode;
    const theme = document.body.dataset.theme;
    const thinking = document.body.classList.contains('is-thinking');
    const audio = window.bacyAudioLevel || 0;
    if (state.mode !== mode) {
      brain.visible = mode !== 'normal'; face.visible = mode === 'normal';
      neuralSurface.visible = mode === 'hardcore';
      material.wireframe = mode === 'hardcore';
      material.transparent = mode === 'hardcore';
      material.opacity = mode === 'hardcore' ? .21 : 1;
      state.mode = mode;
      host.setAttribute('aria-label', mode === 'normal' ? 'Sculpted face without a body or hands' : 'Three-dimensional brain with connected neurons');
    }
    if (state.theme !== theme) {
      const light = theme === 'light';
      material.color.set(light ? 0x779ac5 : 0xaabed4);
      lineMaterial.color.set(light ? 0x548ac5 : 0x5a8ac4);
      nodeMaterial.color.set(light ? 0x397dca : 0x8bc5ff);
      disc.material.color.set(light ? 0xbbd0e7 : 0x152945);
      renderer.toneMappingExposure = light ? 1.05 : 1.25;
      state.theme = theme;
    }
    const bob = Math.sin(seconds * .65) * .045;
    brain.position.y = face.position.y = bob;
    const rotate = reducedMotion ? -.24 : -.24 + Math.sin(seconds * .12) * .13 + state.pointerX * .12;
    brain.rotation.y += (rotate - brain.rotation.y) * .025;
    face.rotation.y += (state.pointerX * .14 - face.rotation.y) * .025;
    material.emissiveIntensity = .15 + audio * .45 + (thinking ? .12 : 0);
    nodeMaterial.opacity = .48 + audio * .35 + (thinking ? .15 : 0);
    lineMaterial.opacity = (mode === 'hardcore' ? .34 : .16) + audio * .24;
    rim.intensity = 3.6 + audio * 2.3;
    for (const pulse of pulses) pulse.mesh.position.copy(pulse.curve.getPoint((seconds * (thinking ? .2 : .07) + pulse.offset) % 1));
    renderer.render(scene, camera);
    frame = requestAnimationFrame(draw);
  }
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { cancelAnimationFrame(frame); stopped = true; }
    else if (stopped) { stopped = false; draw(); }
  });
  window.addEventListener('pagehide', () => { stopped = true; cancelAnimationFrame(frame); observer.disconnect(); renderer.dispose(); });
  draw();
}
