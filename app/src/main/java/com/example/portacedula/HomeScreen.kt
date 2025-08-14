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
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties


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
                title = {
                    Text(
                        "Porta Cédula",
                        color = MaterialTheme.colorScheme.primary, // Solo cambia el color del texto
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors() // Barra igual que antes
            )
        }
        ,
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.toggleAddSheet(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary )
            {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta 3D arriba (ocupa ancho completo)
            item {
                Rotating3DCard(
                    frontUri = ui.frontUri,
                    backUri = ui.backUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ID_CARD_ASPECT)
                        .padding(horizontal = 12.dp)
                )
            }

            // Anverso reducido (90% del ancho)
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth(0.84f)) {
                        IdCardSection(
                            title = "Parte Frontal",
                            uri = ui.frontUri,
                            onAdd = { launchDocScanner { uri -> vm.onFrontCaptured(uri.toString()) } },
                            onOpen = { ui.frontUri?.let(vm::onZoom) }
                        )
                    }
                }
            }

            // Reverso reducido (90% del ancho)
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth(0.84f)) {
                        IdCardSection(
                            title = "Parte Reversa",
                            uri = ui.backUri,
                            onAdd = { launchDocScanner { uri -> vm.onBackCaptured(uri.toString()) } },
                            onOpen = { ui.backUri?.let(vm::onZoom) }
                        )
                    }
                }
            }

            //item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Bottom sheet con opciones del FAB (con scroll por si hay poco alto disponible)
    if (ui.showAddSheet) {
        ModalBottomSheet(onDismissRequest = { vm.toggleAddSheet(false) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

    if (ui.showZoom != null) {
        FullscreenImageViewer(
            uri = ui.showZoom!!,
            onClose = { vm.onZoom(null) }
        )
    }

}


@Composable
fun Rotating3DCard(
    frontUri: String?,
    backUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
          <script src="three/three.min.js"></script>
        </head>
        <body>
          <div id="root"></div>
          <script>
            // -------- Config --------
            const aspect = $ID_CARD_ASPECT;   // 1.586
            const w = aspect, h = 1.0;
            const d = 0.05;                   // grosor visible
            const R = 0.07;                   // radio esquinas del canto
            const autoSpin = 0.005;           // giro automático (mitad de velocidad)

            // -------- Escena/Cámara --------
            const scene = new THREE.Scene();
            const camera = new THREE.PerspectiveCamera(30, window.innerWidth/window.innerHeight, 0.1, 100);
            camera.position.set(0,0,2.8);

            const renderer = new THREE.WebGLRenderer({ antialias:true, alpha:true, premultipliedAlpha:false });
            renderer.setClearColor(0x000000, 0);
            renderer.setSize(window.innerWidth, window.innerHeight);
            document.getElementById('root').appendChild(renderer.domElement);

            // Luces
            const dir = new THREE.DirectionalLight(0xffffff, 1);
            dir.position.set(5,5,5);
            scene.add(dir, new THREE.AmbientLight(0xffffff, 0.5));

            // ---- alphaMap sin halos ----
            function makeRoundedAlpha(aspect, px=1024) {
              const W = px, H = Math.max(1, Math.round(px / aspect));
              const r = Math.round(H * 0.08); // ~8% del alto
              const c = document.createElement('canvas'); c.width=W; c.height=H;
              const g = c.getContext('2d');
              g.clearRect(0,0,W,H); g.fillStyle='white';
              const x=0,y=0;
              g.beginPath();
              g.moveTo(x+r,y);
              g.lineTo(x+W-r,y);
              g.quadraticCurveTo(x+W,y,x+W,y+r);
              g.lineTo(x+W,y+H-r);
              g.quadraticCurveTo(x+W,y+H,x+W-r,y+H);
              g.lineTo(x+r,y+H);
              g.quadraticCurveTo(x,y+H,x,y+H-r);
              g.lineTo(x,y+r);
              g.quadraticCurveTo(x,y,x+r,y);
              g.closePath(); g.fill();
              const tex = new THREE.CanvasTexture(c);
              tex.wrapS = THREE.ClampToEdgeWrapping;
              tex.wrapT = THREE.ClampToEdgeWrapping;
              tex.generateMipmaps = false;
              tex.minFilter = THREE.LinearFilter;
              tex.magFilter = THREE.LinearFilter;
              if (THREE.sRGBEncoding) tex.encoding = THREE.sRGBEncoding; else tex.colorSpace = THREE.SRGBColorSpace;
              tex.needsUpdate = true;
              return tex;
            }
            const alphaTex = makeRoundedAlpha(aspect);

            // Texturas (data URLs)
            const loader = new THREE.TextureLoader();
            const frontTex = loader.load("$front");
            const backTex  = loader.load("$back");
            if (THREE.sRGBEncoding) { frontTex.encoding = THREE.sRGBEncoding; backTex.encoding = THREE.sRGBEncoding; }
            else { frontTex.colorSpace = THREE.SRGBColorSpace; backTex.colorSpace = THREE.SRGBColorSpace; }

            // ---- 1) Caja base: caras con alphaMap, lados invisibles ----
            const matSideInvisible = new THREE.MeshBasicMaterial({ transparent:true, opacity:0.0, depthWrite:false });
            const matFront = new THREE.MeshBasicMaterial({ map: frontTex, alphaMap: alphaTex, transparent:true, alphaTest: 0.9, depthWrite:false });
            const matBack  = new THREE.MeshBasicMaterial({ map: backTex,  alphaMap: alphaTex, transparent:true, alphaTest: 0.9, depthWrite:false });

            const cardBox = new THREE.Mesh(
              new THREE.BoxGeometry(w,h,d),
              [matSideInvisible, matSideInvisible, matSideInvisible, matSideInvisible, matFront, matBack]
            );
            scene.add(cardBox);

            // ---- 2) Canto redondeado real (Extrude, solo lados) ----
            function roundedRectShape(width, height, radius) {
              const x = -width/2, y = -height/2;
              const r = Math.min(radius, width/2, height/2);
              const s = new THREE.Shape();
              s.moveTo(x+r, y);
              s.lineTo(x+width-r, y);
              s.quadraticCurveTo(x+width, y, x+width, y+r);
              s.lineTo(x+width, y+height-r);
              s.quadraticCurveTo(x+width, y+height, x+width-r, y+height);
              s.lineTo(x+r, y+height);
              s.quadraticCurveTo(x, y+height, x, y+height-r);
              s.lineTo(x, y+r);
              s.quadraticCurveTo(x, y, x+r, y);
              return s;
            }
            const shape = roundedRectShape(w, h, R);
            const sideGeom = new THREE.ExtrudeGeometry(shape, { depth: d, bevelEnabled: false, curveSegments: 24, steps: 1 });
            sideGeom.center();

            const sideMat = new THREE.MeshPhongMaterial({ color: 0x444444, shininess: 8, specular: 0x222222 });
            sideMat.polygonOffset = true; sideMat.polygonOffsetFactor = 1; sideMat.polygonOffsetUnits = 1;
            const capTransparent = new THREE.MeshBasicMaterial({ transparent:true, opacity:0.0, depthWrite:false });
            const sideMesh = new THREE.Mesh(sideGeom, [sideMat, capTransparent, capTransparent]);
            if (sideGeom.groups?.length >= 3) {
              sideGeom.groups[0].materialIndex = 0;
              if (sideGeom.groups[1]) sideGeom.groups[1].materialIndex = 1;
              if (sideGeom.groups[2]) sideGeom.groups[2].materialIndex = 2;
            }
            sideMesh.scale.set(0.992, 0.992, 1.0); // micro-inset bajo la máscara
            scene.add(sideMesh);

            // ---- Interacción: arrastre SOLO eje Y (horizontal) con yaw estable ----
            let isDragging = false;
            let startX = 0;
            let yawStart = 0;   // yaw al iniciar drag
            let yaw = 0;        // yaw actual (fuente de verdad)
            const dragSensitivity = 0.005;

            function applyRotation(rx, ry) {
              cardBox.rotation.x = rx; cardBox.rotation.y = ry;
              sideMesh.rotation.x = rx; sideMesh.rotation.y = ry;
            }

            const el = renderer.domElement;
            el.style.touchAction = 'none';

            el.addEventListener('pointerdown', (e) => {
              isDragging = true;
              startX = e.clientX;
              yawStart = yaw;               // no reiniciamos: guardamos estado
              el.setPointerCapture(e.pointerId);
              e.preventDefault();
            }, { passive:false });

            el.addEventListener('pointermove', (e) => {
              if (!isDragging) return;
              const dx = e.clientX - startX;
              yaw = yawStart + dx * dragSensitivity; // yaw continuo sin saltos
              e.preventDefault();
            }, { passive:false });

            function stopDrag(e) {
              isDragging = false;
              if (e) e.preventDefault();
            }
            el.addEventListener('pointerup', stopDrag);
            el.addEventListener('pointercancel', stopDrag);
            el.addEventListener('pointerleave', stopDrag);

            // ---- Animación (autoSpin cuando NO arrastras) ----
            function animate() {
              requestAnimationFrame(animate);
              if (!isDragging) yaw += autoSpin;
              applyRotation(0, yaw);
              renderer.render(scene, camera);
            }
            animate();

            // Resize
            window.addEventListener('resize', () => {
              camera.aspect = window.innerWidth / window.innerHeight;
              camera.updateProjectionMatrix();
              renderer.setSize(window.innerWidth, window.innerHeight);
            });
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // --- ajustes clave ---
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                setOnTouchListener { v, ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            // Evita que la LazyColumn intercepte el gesto
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    // Deja que el WebView procese el evento normalmente
                    false
                }

                // --- lo que ya tenías ---
                WebView.setWebContentsDebuggingEnabled(true)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "WV",
                            "${cm.messageLevel()}: ${cm.message()} @${cm.sourceId()}:${cm.lineNumber()}"
                        )
                        return super.onConsoleMessage(cm)
                    }
                }
            }
        },
        update = { webView ->
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

@Composable
fun IdCardSection(
    title: String,
    uri: String?,
    onAdd: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ID_CARD_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE0E0E0))
                .clickable { if (uri != null) onOpen() else onAdd() },
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("Agregar", color = Color.Gray)
            }
        }
    }
}

@Composable
fun FullscreenImageViewer(
    uri: String,
    onClose: () -> Unit
) {
    // Estado de zoom/traslación
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        // Ajustar pan proporcional al zoom
        if (newScale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f; offsetY = 0f
        }
        scale = newScale
    }

    BackHandler(onBack = onClose)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,   // ⬅️ ocupa toda la pantalla
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Imagen (fit, con zoom/drag)
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,     // muestra completa (tipo “viewer”)
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // doble toque: reset
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            },
                            onTap = { /* si quieres, alterna UI */ }
                        )
                    }
            )

            // Botón cerrar (arriba-izquierda)
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}
