export function createParticleRenderer(canvas:HTMLCanvasElement){
  let gl:WebGLRenderingContext|null=null,program:WebGLProgram|null=null,buffer:WebGLBuffer|null=null;
  const shaders:WebGLShader[]=[];
  let disposed=false;
  const dispose=()=>{
    if(disposed)return;
    disposed=true;
    if(!gl)return;
    if(buffer)gl.deleteBuffer(buffer);
    if(program)gl.deleteProgram(program);
    for(const shader of shaders)gl.deleteShader(shader);
  };
  try{
    gl=canvas.getContext('webgl',{alpha:true,premultipliedAlpha:false});
    if(!gl)return null;
    const compile=(type:number,source:string)=>{
      const shader=gl!.createShader(type);
      if(!shader)throw new Error('Particle shader unavailable');
      shaders.push(shader);gl!.shaderSource(shader,source);gl!.compileShader(shader);
      if(!gl!.getShaderParameter(shader,gl!.COMPILE_STATUS))throw new Error('Particle shader compilation failed');
      return shader;
    };
    const vertex=compile(gl.VERTEX_SHADER,'attribute vec2 pos; uniform float size; void main(){gl_Position=vec4(pos,0.,1.);gl_PointSize=size;}');
    const fragment=compile(gl.FRAGMENT_SHADER,'precision mediump float; uniform vec4 color; void main(){float d=length(gl_PointCoord-vec2(.5))*2.;gl_FragColor=vec4(color.rgb,color.a*pow(max(0.,1.-d),2.));}');
    program=gl.createProgram();if(!program)throw new Error('Particle program unavailable');
    gl.attachShader(program,vertex);gl.attachShader(program,fragment);gl.linkProgram(program);
    if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error('Particle program linking failed');
    buffer=gl.createBuffer();if(!buffer)throw new Error('Particle buffer unavailable');
    gl.useProgram(program);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
    const location=gl.getAttribLocation(program,'pos');
    if(location<0)throw new Error('Particle attribute unavailable');
    gl.enableVertexAttribArray(location);gl.vertexAttribPointer(location,2,gl.FLOAT,false,0,0);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE);
    const size=gl.getUniformLocation(program,'size'),color=gl.getUniformLocation(program,'color');
    return {gl,size,color,dispose};
  }catch(error){
    dispose();console.warn('Arena particles unavailable',error);return null;
  }
}
