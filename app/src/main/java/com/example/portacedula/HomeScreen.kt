@file:Suppress("SetJavaScriptEnabled")
package com.example.portacedula

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import kotlin.math.abs

import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View



// Proporción real de una cédula/tarjeta (ISO/IEC 7810 ID‑1: 85.60 × 53.98 mm)
private const val ID_CARD_ASPECT = 1.586f



// ---------- extensión para obtener Activity ----------
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context.findActivity()

    var pendingSide by remember { mutableStateOf<((Uri) -> Unit)?>(null) }

    // Launcher del Document Scanner
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { ar ->
        if (ar.resultCode == Activity.RESULT_OK) {
            val res = GmsDocumentScanningResult.fromActivityResultIntent(ar.data)
            res?.pages?.firstOrNull()?.imageUri?.let { uri ->
                pendingSide?.invoke(uri)
            }
        }
    }

    fun launchDocScanner(onImage: (Uri) -> Unit) {
        val act = activity ?: run {
            Toast.makeText(context, "No se encontró Activity", Toast.LENGTH_SHORT).show()
            return
        }
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE) // UI con cuadro/guía
            .build()

        val scanner = GmsDocumentScanning.getClient(opts)
        pendingSide = onImage
        scanner.getStartScanIntent(act)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al abrir el escáner", Toast.LENGTH_SHORT).show()
            }
    }

    // -------- UI principal --------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Porta Cedula") }, // alineado a la izquierda por defecto
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.toggleAddSheet(true) }) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Rotating3DCard(
                frontUri = ui.frontUri,
                backUri = ui.backUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ID_CARD_ASPECT)
            )

            IdCardSection(
                title = "Parte anverso",
                uri = ui.frontUri,
                onAdd = { launchDocScanner { uri -> vm.onFrontCaptured(uri.toString()) } },
                onOpen = { ui.frontUri?.let(vm::onZoom) }
            )
            IdCardSection(
                title = "Parte reverso",
                uri = ui.backUri,
                onAdd = { launchDocScanner { uri -> vm.onBackCaptured(uri.toString()) } },
                onOpen = { ui.backUri?.let(vm::onZoom) }
            )
        }
    }

    // Bottom sheet con opciones del FAB
    if (ui.showAddSheet) {
        ModalBottomSheet(onDismissRequest = { vm.toggleAddSheet(false) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Rotating3DCard(
                    frontUri = ui.frontUri,
                    backUri  = ui.backUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ID_CARD_ASPECT) // mantiene formato de tarjeta
                )

                Text("Agregar", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("Agregar anverso") },
                    modifier = Modifier.clickable {
                        vm.toggleAddSheet(false)
                        launchDocScanner { uri -> vm.onFrontCaptured(uri.toString()) }
                    }
                )
                ListItem(
                    headlineContent = { Text("Agregar reverso") },
                    modifier = Modifier.clickable {
                        vm.toggleAddSheet(false)
                        launchDocScanner { uri -> vm.onBackCaptured(uri.toString()) }
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Zoom de imagen (opción A: llena contenedor de tarjeta, recorta bordes con Crop)
    if (ui.showZoom != null) {
        Dialog(onDismissRequest = { vm.onZoom(null) }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ID_CARD_ASPECT) // mantiene proporción de tarjeta en el zoom
                        .clickable { vm.onZoom(null) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ui.showZoom,
                        contentDescription = null,
                        contentScale = ContentScale.Crop, // ← recorta bordes extra del escáner
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun IdCardSection(
    title: String,
    uri: String?,
    onAdd: () -> Unit,
    onOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ID_CARD_ASPECT) // ← formato ID‑1 exacto
                .clickable { if (uri != null) onOpen() else onAdd() },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (uri == null) {
                    Text("Toca para agregar", textAlign = TextAlign.Center)
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = title,
                        contentScale = ContentScale.Crop, // ← ajusta, recortando bordes si sobran
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}



@Composable
fun Rotating3DCard(
    frontUri: String?,
    backUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Convierte las URIs a data URL base64 para que el WebView las pueda cargar offline
    var frontDataUrl by remember { mutableStateOf<String?>(null) }
    var backDataUrl  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(frontUri) {
        frontDataUrl = frontUri?.let { u ->
            context.contentResolver.openInputStream(android.net.Uri.parse(u))?.use {
                "data:image/jpeg;base64," + Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            }
        } ?: placeholderSvgBase64("Anverso")
    }
    LaunchedEffect(backUri) {
        backDataUrl = backUri?.let { u ->
            context.contentResolver.openInputStream(android.net.Uri.parse(u))?.use {
                "data:image/jpeg;base64," + Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
            }
        } ?: placeholderSvgBase64("Reverso")
    }

    val html = remember(frontDataUrl, backDataUrl) {
        val front = frontDataUrl ?: placeholderSvgBase64("Anverso")
        val back  = backDataUrl  ?: placeholderSvgBase64("Reverso")

        // Importante: script apunta a assets (r160) y tenemos logs en consola
        """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/>
          <style>
            html,body { margin:0; height:100%; background:transparent; overflow:hidden; }
            #root { position:fixed; inset:0; }
            canvas { width:100%; height:100%; display:block; }
          </style>
          <script>
            // logger simple
            (function(){
              const oldLog = console.log, oldErr = console.error;
              console.log = function(){ oldLog.apply(console, arguments); };
              console.error = function(){ oldErr.apply(console, arguments); };
            })();
          </script>
          <script src="three/three.min.js"></script>
        </head>
        <body>
          <div id="root"></div>
          <script>
            try {
              // Verificar WebGL
              function webglAvailable() {
                try {
                  const c = document.createElement('canvas');
                  return !!(window.WebGLRenderingContext && (c.getContext('webgl') || c.getContext('experimental-webgl')));
                } catch (e) { return false; }
              }
              if (!webglAvailable()) {
                document.body.style.background = 'transparent';
                const msg = document.createElement('div');
                msg.style.cssText='position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#888;font-family:sans-serif';
                msg.textContent = 'WebGL no disponible';
                document.body.appendChild(msg);
              } else {
                const scene = new THREE.Scene();
                const camera = new THREE.PerspectiveCamera(35, window.innerWidth/window.innerHeight, 0.1, 100);
                camera.position.set(0,0,4);

                const renderer = new THREE.WebGLRenderer({antialias:true, alpha:true});
                renderer.setClearColor(0x000000, 0);
                renderer.setSize(window.innerWidth, window.innerHeight);
                document.getElementById('root').appendChild(renderer.domElement);

                // Luces
                const dir = new THREE.DirectionalLight(0xffffff, 1);
                dir.position.set(5,5,5);
                scene.add(dir);
                scene.add(new THREE.AmbientLight(0xffffff, 0.5));

                // Texturas (data URLs)
                const loader = new THREE.TextureLoader();
                const frontTex = loader.load("$front", () => console.log('front loaded'), undefined, (e)=>console.error('front err', e));
                const backTex  = loader.load("$back",  () => console.log('back loaded'),  undefined, (e)=>console.error('back err', e));

                if (THREE.sRGBEncoding) {
                  frontTex.encoding = THREE.sRGBEncoding;
                  backTex.encoding  = THREE.sRGBEncoding;
                } else {
                  frontTex.colorSpace = THREE.SRGBColorSpace;
                  backTex.colorSpace  = THREE.SRGBColorSpace;
                }

                // Geometría tarjeta
                const aspect = $ID_CARD_ASPECT;
                const w = aspect, h = 1, d = 0.02;
                const mats = [
                  new THREE.MeshPhongMaterial({color:0x888888}),
                  new THREE.MeshPhongMaterial({color:0x888888}),
                  new THREE.MeshPhongMaterial({color:0x888888}),
                  new THREE.MeshPhongMaterial({color:0x888888}),
                  new THREE.MeshBasicMaterial({map: frontTex}),
                  new THREE.MeshBasicMaterial({map: backTex})
                ];
                const geom = new THREE.BoxGeometry(w,h,d);
                const card = new THREE.Mesh(geom, mats);
                scene.add(card);

                function animate() {
                  requestAnimationFrame(animate);
                  card.rotation.y += 0.01;
                  renderer.render(scene, camera);
                }
                animate();

                window.addEventListener('resize', () => {
                  camera.aspect = window.innerWidth / window.innerHeight;
                  camera.updateProjectionMatrix();
                  renderer.setSize(window.innerWidth, window.innerHeight);
                });
              }
            } catch (err) {
              console.error('Three init error:', err);
              const msg = document.createElement('pre');
              msg.textContent = String(err);
              msg.style.cssText='position:absolute;inset:0;color:#f55;background:#1118;padding:12px;white-space:pre-wrap';
              document.body.appendChild(msg);
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // Debug útil: mira Logcat (WebView)
                WebView.setWebContentsDebuggingEnabled(true)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                // fuerza aceleración HW (por si el Activity la tiene off)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        android.util.Log.d("WV", "${cm.messageLevel()}: ${cm.message()} @${cm.sourceId()}:${cm.lineNumber()}")
                        return super.onConsoleMessage(cm)
                    }
                }
            }
        },
        update = { webView ->
            // Base en assets para que <script src="three/three.min.js"> funcione OFFLINE
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    )
}

private fun placeholderSvgBase64(text: String): String {
    val svg = """
        <svg xmlns='http://www.w3.org/2000/svg' width='856' height='540'>
            <rect width='100%' height='100%' fill='#DDDDDD'/>
            <text x='50%' y='50%' dominant-baseline='middle' text-anchor='middle'
                  fill='#666' font-size='42'>$text</text>
        </svg>
    """.trimIndent()
    return "data:image/svg+xml;base64," + Base64.encodeToString(svg.toByteArray(), Base64.NO_WRAP)
}