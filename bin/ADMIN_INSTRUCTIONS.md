Resumen compacto — acciones recientes y pasos rápidos

1) Estado actual
- Formulario admin corregido (templates/admin/folletos_form.html).
- Validación cliente y servidor para archivos (tamaño y tipos). Límite por defecto: 50 MB.
- FileStorageService guarda archivos en file.storage.location (por defecto ./uploads/{id}/).
- Endpoints para servir/streaming y forzar descarga: /files/{id}/pdf y /files/{id}/audio?download=true
- Botón "Escuchar" abre reproductor modal; "Descargar" descarga o muestra modal para elegir formato.

2) Cómo asociar PDF/MP3 a un folleto (rápido)
- Método recomendado (UI): Login como admin -> Admin Folletos -> Agregar/Editar -> subir PDF y/o MP3 -> Guardar.
- Manual (si ya tienes archivos): copiar archivos a uploads/{id}/ y actualizar los campos pdfFilename/audioFilename en la tabla 'folleto'.
- Importación masiva: puedo implementar un importador CSV si lo solicitas.

3) Pruebas rápidas (local)
- Asegúrate de MySQL corriendo y propiedades en src/main/resources/application.properties.
- Reinicia la app: mvn spring-boot:run o java -jar target\contactos-0.0.1-SNAPSHOT.war
- Admin: http://localhost:8080/admin/folletos/add  (usuario: admin  contraseña: admin)
- Lista: http://localhost:8080/folletos

4) Si no ves los cambios en la pantalla del chat
- No puedo limpiar tu interfaz. Abre este archivo ADMIN_INSTRUCTIONS.md en tu editor o navegador del proyecto para leer el resumen.
- También puedes abrir templates/admin/folletos_form.html directamente: src/main/resources/templates/admin/folletos_form.html

5) ¿Quieres que implemente el importador CSV ahora?
- Responde: "Sí, implementa importador" o elige otra tarea.

Fin de resumen.
