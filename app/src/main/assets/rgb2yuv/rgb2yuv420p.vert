attribute vec4 vPosition;    
attribute vec2 a_texCoord; 
uniform mat4 vMatrixs;
varying vec2 vTextureCoord;

void main() 
{
	gl_Position = vMatrixs * vPosition;
//	gl_Position = vPosition;
	vTextureCoord = a_texCoord;
}