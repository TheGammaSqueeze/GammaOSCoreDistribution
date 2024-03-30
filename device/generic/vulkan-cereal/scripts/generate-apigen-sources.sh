# The encoders use the prefix GL while the decoders use the prefix GLES
cp -f protocols/gles1/gles1.attrib  protocols/gles1/gl.attrib
cp -f protocols/gles1/gles1.in      protocols/gles1/gl.in
cp -f protocols/gles1/gles1.types   protocols/gles1/gl.types
./build/gfxstream-generic-apigen -i ./protocols/gles1 -D ./stream-servers/gles1_dec gles1
./build/gfxstream-generic-apigen -i ./protocols/gles1 -E ../goldfish-opengl/system/GLESv1_enc gl
rm protocols/gles1/gl.attrib
rm protocols/gles1/gl.in
rm protocols/gles1/gl.types

cp -f protocols/gles2/gles2.attrib  protocols/gles2/gl2.attrib
cp -f protocols/gles2/gles2.in      protocols/gles2/gl2.in
cp -f protocols/gles2/gles2.types   protocols/gles2/gl2.types
./build/gfxstream-generic-apigen -i ./protocols/gles2 -D ./stream-servers/gles2_dec gles2
./build/gfxstream-generic-apigen -i ./protocols/gles2 -E ../goldfish-opengl/system/GLESv2_enc gl2
rm protocols/gles2/gl2.attrib
rm protocols/gles2/gl2.in
rm protocols/gles2/gl2.types

./build/gfxstream-generic-apigen -i ./protocols/renderControl -D ./stream-servers/renderControl_dec renderControl
./build/gfxstream-generic-apigen -i ./protocols/renderControl -E ../goldfish-opengl/system/renderControl_enc renderControl
