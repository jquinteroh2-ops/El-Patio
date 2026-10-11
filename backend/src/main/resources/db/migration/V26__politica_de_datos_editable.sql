-- La política de tratamiento de datos pasa a la base, editable desde el panel.
--
-- Antes estaba escrita en el código, con un aviso de «borrador» encima, y cada
-- corrección exigía un despliegue. Ahora es un bloque más de
-- contenido_institucional: el restaurante la corrige en Panel → Institucional y
-- se ve en /politica-de-datos al instante.
--
-- Va en la misma tabla que «Quiénes somos» porque es exactamente lo mismo: un
-- título y un texto plano que edita una persona. El sitio la saca de la sección
-- de la portada por su clave y la pinta en su propia página.
--
-- Formato del cuerpo: texto plano. Una línea que empieza por «# » es un
-- subtítulo; los párrafos se separan con una línea en blanco.
--
-- El texto es el que ya estaba publicado, sin el NIT ni el correo de ejemplo
-- que traía el código: un NIT inventado en una política pública es declarar
-- algo falso. El restaurante los agrega al editarla.

insert into contenido_institucional (clave, titulo, cuerpo, orden, visible, actualizado_en) values
('politica-datos', 'Política de tratamiento de datos personales',
'# Quién responde por sus datos
Restaurante El Patio, con domicilio en la Calle 26 #31-2, Turbaco, Bolívar, es el responsable del tratamiento de los datos personales que usted entregue a través de este sitio.

# Para qué los usamos
Los datos que se envían por el formulario de «Trabaja con nosotros» —nombre, documento de identidad, teléfono, correo electrónico y hoja de vida— se usan únicamente para evaluar su candidatura a una vacante y para comunicarnos con usted durante ese proceso. No se venden, no se comparten con terceros con fines comerciales y no se usan para enviarle publicidad.

Los datos de una reserva, un pedido o una PQR se usan solo para atenderla y para responderle.

# Cuánto tiempo los guardamos
Conservamos su hoja de vida mientras el proceso de selección esté abierto y por un periodo razonable después, por si surge una vacante que encaje con su perfil. Usted puede pedir que la eliminemos en cualquier momento.

# Sus derechos
La Ley 1581 de 2012 le da derecho a conocer, actualizar y rectificar sus datos, a solicitar prueba de la autorización que otorgó, a ser informado sobre el uso que se les ha dado, a presentar quejas ante la Superintendencia de Industria y Comercio y a solicitar la supresión de sus datos.

Para ejercer cualquiera de estos derechos comuníquese al +57 304 403 2936. Atendemos su solicitud en los términos que fija la ley.

# Seguridad
Su hoja de vida se guarda en un almacenamiento al que solo accede la administración del restaurante, mediante una sesión autenticada. No queda publicada en ninguna dirección accesible desde internet.

# Cookies y analítica
Si usted lo acepta en el aviso de cookies, el sitio usa Google Analytics para contar las visitas y saber qué páginas se consultan. Google recibe datos técnicos de la visita —páginas vistas, tipo de dispositivo, ciudad aproximada—, no su nombre ni sus datos de contacto, y no los usamos para publicidad. Si no acepta, Google Analytics no se carga. Puede cambiar su decisión cuando quiera desde el enlace «Cookies» al pie de cada página. Las pantallas del personal del restaurante nunca se miden.',
 100, true, now());
