import * as THREE from './vendor/three.module.min.js';

// Original portrait artwork with animated neural pathways and facial deformation.
// This is an animated portrait, not a camera feed or a fully rigged 3D person.
const host = document.getElementById('visual-scene');
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
let renderer;
try { renderer = new THREE.WebGLRenderer({ antialias:true, alpha:true, powerPreference:'low-power' }); }
catch { document.getElementById('scene-fallback').hidden = false; }
if (renderer) init();
function init() {
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));
  renderer.setClearColor(0x000000,0); host.append(renderer.domElement);
  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(-3,3,2,-2,.1,100);camera.position.z=10;
  const texture = new THREE.TextureLoader().load('/portraits.png',undefined,undefined,()=>{document.getElementById('scene-fallback').hidden=false;renderer.domElement.hidden=true;});
  texture.minFilter=THREE.LinearFilter;texture.magFilter=THREE.LinearFilter;
  const uniforms = {
    uPortrait:{value:1},uPrevious:{value:1},uTransition:{value:1},uTime:{value:0},uSpeaking:{value:0},
    uSmile:{value:0},uAnger:{value:0},uBlink:{value:0},uAudio:{value:0},uLight:{value:0},uCrystal:{value:0},uTexture:{value:texture}
  };
  const material = new THREE.ShaderMaterial({transparent:true,uniforms,vertexShader:`varying vec2 vUv;void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}`,fragmentShader:`
    uniform sampler2D uTexture;uniform float uPortrait,uPrevious,uTransition,uTime,uSpeaking,uSmile,uAnger,uBlink,uAudio,uLight,uCrystal;varying vec2 vUv;
    float area(vec2 p,vec2 c,vec2 r){return exp(-dot((p-c)/r,(p-c)/r));}
    vec4 portrait(float id){
      vec2 p=vUv;float mouthY=mix(.31,.292,step(.5,id)* (1.0-step(1.5,id)));
      float lips=area(p,vec2(.5,mouthY),vec2(.17,.055));
      p.y+=lips*uSmile*(abs(p.x-.5)-.065)*.23;
      p.y+=lips*uSpeaking*(p.y-mouthY)*-.6;
      float eyes=area(p,vec2(.345,.55),vec2(.1,.044))+area(p,vec2(.65,.55),vec2(.1,.044));
      p.y+=eyes*uBlink*.032;
      p.y+=uAnger*area(p,vec2(.5,.59),vec2(.25,.04))*(.065-abs(p.x-.5))*.18;
      vec4 c=texture2D(uTexture,vec2((clamp(p.x,.002,.998)+id)/3.0,clamp(p.y,.002,.998)));
      float lipOpen=area(vUv,vec2(.5,mouthY),vec2(.075,.009+uSpeaking*.012));
      float brightness=max(c.r,max(c.g,c.b));
      c.rgb*=1.0-lipOpen*uSpeaking*.48;
      c.rgb+=vec3(.16,.4,.37)*(uAudio*.055+uSpeaking*.02)*eyes;
      float scan=sin(vUv.y*790.0-uTime*1.3)*.011*(1.0-step(.5,id));c.rgb+=scan;
      c.rgb=mix(c.rgb,c.rgb*vec3(.94,.83,1.08),uCrystal*.65);
      float edge=smoothstep(0.0,.09,vUv.x)*smoothstep(0.0,.09,1.0-vUv.x)*smoothstep(0.0,.15,vUv.y)*smoothstep(0.0,.025,1.0-vUv.y);
      c.a=edge*smoothstep(.045,.13,brightness);
      return c;
    }
    void main(){gl_FragColor=mix(portrait(uPrevious),portrait(uPortrait),smoothstep(0.0,1.0,uTransition));}
  `});
  const portrait = new THREE.Mesh(new THREE.PlaneGeometry(3.15*661/793,3.15),material);portrait.position.z=.1;scene.add(portrait);
  const network = new THREE.Group();scene.add(network);
  const random = (()=>{let seed=221;return()=>{seed=(seed*16807)%2147483647;return(seed-1)/2147483646;};})();
  const nodes=[];
  for(let i=0;i<115;i++){
    const side=i%2?1:-1;const x=side*(1.05+random()*1.65);const y=(random()-.5)*2.7;
    nodes.push(new THREE.Vector3(x,y,-.3));
  }
  const nodeMaterial=new THREE.PointsMaterial({color:0x94ced3,size:.012,transparent:true,opacity:.55});
  network.add(new THREE.Points(new THREE.BufferGeometry().setFromPoints(nodes),nodeMaterial));
  const links=[];
  for(let i=0;i<nodes.length;i++)for(let j=i+1;j<nodes.length;j++)if(nodes[i].distanceTo(nodes[j])<.45&&random()>.4)links.push(nodes[i],nodes[j]);
  const lineMaterial=new THREE.LineBasicMaterial({color:0x60868a,transparent:true,opacity:.14});
  network.add(new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(links),lineMaterial));
  const pulses=[];
  for(let i=0;i<10;i++){
    const side=i%2?1:-1;const y=(i/10-.5)*2.3;
    const curve=new THREE.CubicBezierCurve3(new THREE.Vector3(side*.78,y*.6,-.1),new THREE.Vector3(side*1.25,y+.3,-.25),new THREE.Vector3(side*2,y-.4,-.3),new THREE.Vector3(side*2.8,y*.85,-.4));
    network.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(curve.getPoints(60)),new THREE.LineBasicMaterial({color:0x91cfd2,transparent:true,opacity:.14})));
    const pulse=new THREE.Mesh(new THREE.SphereGeometry(.016,6,6),new THREE.MeshBasicMaterial({color:0xb8e4d5,transparent:true,opacity:.75}));network.add(pulse);pulses.push({pulse,curve,offset:random()});
  }
  const ring=new THREE.Mesh(new THREE.RingGeometry(1.25,1.255,128),new THREE.MeshBasicMaterial({color:0xa5d9c8,transparent:true,opacity:.13,side:THREE.DoubleSide}));
  ring.scale.y=.12;ring.position.set(0,-1.65,-.2);scene.add(ring);
  let mode='', lastFrame=0, pointerX=0, pointerY=0, transitionAt=0;
  host.addEventListener('pointermove',e=>{const r=host.getBoundingClientRect();pointerX=(e.clientX-r.left)/r.width-.5;pointerY=(e.clientY-r.top)/r.height-.5;});
  host.addEventListener('pointerleave',()=>{pointerX=pointerY=0;});
  const resize=()=>{const r=host.getBoundingClientRect();if(!r.width||!r.height)return;renderer.setSize(r.width,r.height);camera.left=-2*r.width/r.height;camera.right=2*r.width/r.height;camera.updateProjectionMatrix();const mobile=r.width<750;portrait.scale.setScalar(mobile?.88:1.12);portrait.position.set(0,-.06,.1);network.scale.setScalar(mobile?.8:1);};
  new ResizeObserver(resize).observe(host);resize();
  function animate(now){
    requestAnimationFrame(animate);if(document.hidden||now-lastFrame<33)return;lastFrame=now;
    const t=reduced?0:now/1000, next=document.body.dataset.mode;
    if(next!==mode){uniforms.uPrevious.value=uniforms.uPortrait.value;uniforms.uPortrait.value=next==='normal'?0:next==='professional'?1:2;transitionAt=now;mode=next;host.dataset.portrait=next;}
    uniforms.uTransition.value=reduced?1:Math.min(1,(now-transitionAt)/650);
    uniforms.uTime.value=t;uniforms.uAudio.value=window.bacyAudioLevel||0;
    const mood=document.body.dataset.mood;
    uniforms.uSmile.value=mood==='happy'?1:mood==='playful'?1.6:mood==='angry'?-.6:0;
    uniforms.uAnger.value=mood==='angry'?1:mood==='serious'?.45:0;
    uniforms.uCrystal.value=mode==='crystal'?1:0;
    const speaking=document.body.classList.contains('is-speaking');
    uniforms.uSpeaking.value=reduced?0:speaking?.3+Math.abs(Math.sin(t*9)*Math.sin(t*13))*.6:0;
    const blinkPhase=t%6.5;uniforms.uBlink.value=reduced?0:blinkPhase>6.25?Math.sin((blinkPhase-6.25)/.25*Math.PI):0;
    if(!reduced){portrait.position.x+=(pointerX*.08-portrait.position.x+(0))*.035;portrait.rotation.y+=(pointerX*.035-portrait.rotation.y)*.03;portrait.rotation.z=Math.sin(t*.35)*.002+pointerY*.005;network.rotation.z=Math.sin(t*.08)*.007;
    pulses.forEach(({pulse,curve,offset})=>pulse.position.copy(curve.getPoint((t*(document.body.classList.contains('is-thinking')?.18:.07)+offset)%1)));}
    const active=document.body.classList.contains('is-listening')||speaking;
    nodeMaterial.opacity=active?.75:.4;lineMaterial.opacity=active?.24:.13;
    renderer.render(scene,camera);
  }
  requestAnimationFrame(animate);
}
