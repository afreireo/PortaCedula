package com.example.portacedula

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdCardSection(
    title: String,
    uri: String?,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 400),
        label = "itemBackgroundColor"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        label = "itemScale"
    )

    Column(modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ID_CARD_ASPECT_RATIO)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .combinedClickable(
                    onClick = { if (uri != null) onOpen() else onAdd() },
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("Agregar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)))
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Composable
fun Rotating3DCard(
    frontUri: String?, 
    backUri: String?, 
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var currentFrontUrl by remember { mutableStateOf(placeholderSvgBase64("Anverso")) }
    var currentBackUrl by remember { mutableStateOf(placeholderSvgBase64("Reverso")) }

    // Función para procesar y optimizar imágenes
    fun processImage(uriStr: String?): String? {
        if (uriStr == null) return null
        return try {
            context.contentResolver.openInputStream(uriStr.toUri())?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeStream(input, null, options)
                val out = ByteArrayOutputStream()
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 60, out)
                bitmap?.recycle()
                "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) { null }
    }

    LaunchedEffect(frontUri) {
        currentFrontUrl = processImage(frontUri) ?: placeholderSvgBase64("Anverso")
    }

    LaunchedEffect(backUri) {
        currentBackUrl = processImage(backUri) ?: placeholderSvgBase64("Reverso")
    }

    val html = remember(currentFrontUrl, currentBackUrl) {
        """
        <!doctype html><html><head><meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no"/>
        <style>html,body { margin:0; height:100%; background:transparent; overflow:hidden; } #root { position:fixed; inset:0; } canvas { width:100%; height:100%; display:block; touch-action: none; }</style>
        <script src="three/three.min.js"></script></head>
        <body><div id="root"></div><script>
            const aspect = $ID_CARD_ASPECT_RATIO;
            const w = aspect, h = 1, d = 0.015, r = 0.07;
            
            const scene = new THREE.Scene();
            const camera = new THREE.PerspectiveCamera(30, window.innerWidth/window.innerHeight, 0.1, 100);
            camera.position.set(0,0,2.5);
            const renderer = new THREE.WebGLRenderer({ antialias:true, alpha:true });
            renderer.setClearColor(0x000000, 0);
            renderer.setPixelRatio(window.devicePixelRatio);
            renderer.setSize(window.innerWidth, window.innerHeight);
            document.getElementById('root').appendChild(renderer.domElement);
            scene.add(new THREE.AmbientLight(0xffffff, 1.0));

            const shape = new THREE.Shape();
            shape.moveTo(-w/2 + r, -h/2);
            shape.lineTo(w/2 - r, -h/2);
            shape.absarc(w/2 - r, -h/2 + r, r, -Math.PI/2, 0, false);
            shape.lineTo(w/2, h/2 - r);
            shape.absarc(w/2 - r, h/2 - r, r, 0, Math.PI/2, false);
            shape.lineTo(-w/2 + r, h/2);
            shape.absarc(-w/2 + r, h/2 - r, r, Math.PI/2, Math.PI, false);
            shape.lineTo(-w/2, -h/2 + r);
            shape.absarc(-w/2 + r, -h/2 + r, r, Math.PI, Math.PI * 1.5, false);

            const loader = new THREE.TextureLoader();
            const fTex = loader.load("$currentFrontUrl");
            const bTex = loader.load("$currentBackUrl");
            fTex.colorSpace = bTex.colorSpace = THREE.SRGBColorSpace;

            function createCap(tex, z, isBack = false) {
                const geom = new THREE.ShapeGeometry(shape);
                const pos = geom.attributes.position;
                const uvs = new Float32Array(pos.count * 2);
                for (let i = 0; i < pos.count; i++) {
                    let u = (pos.getX(i) + w/2) / w;
                    let v = (pos.getY(i) + h/2) / h;
                    uvs[i*2] = u; uvs[i*2+1] = v;
                }
                geom.setAttribute('uv', new THREE.BufferAttribute(uvs, 2));
                const mesh = new THREE.Mesh(geom, new THREE.MeshBasicMaterial({ map: tex, transparent: true }));
                mesh.position.z = z;
                if (isBack) mesh.rotation.y = Math.PI;
                return mesh;
            }

            const card = new THREE.Group();
            card.add(createCap(fTex, d/2 + 0.005));
            card.add(createCap(bTex, -d/2 - 0.005, true));

            const edgeGeom = new THREE.ExtrudeGeometry(shape, { depth: d, bevelEnabled: false });
            edgeGeom.center();
            const edgeMesh = new THREE.Mesh(edgeGeom, new THREE.MeshBasicMaterial({ color: 0xeeeeee }));
            card.add(edgeMesh);

            scene.add(card);

            let isDragging = false, startX = 0, yaw = 0, autoSpin = 0.005;
            const el = renderer.domElement;
            el.addEventListener('pointerdown', e => { isDragging = true; startX = e.clientX; el.setPointerCapture(e.pointerId); });
            el.addEventListener('pointermove', e => { if(isDragging) { yaw += (e.clientX - startX) * 0.008; startX = e.clientX; } });
            el.addEventListener('pointerup', () => isDragging = false);

            function animate() {
                requestAnimationFrame(animate);
                if(!isDragging) yaw += autoSpin;
                card.rotation.y = yaw;
                renderer.render(scene, camera);
            }
            animate();
            window.addEventListener('resize', () => {
                camera.aspect = window.innerWidth / window.innerHeight;
                camera.updateProjectionMatrix();
                renderer.setSize(window.innerWidth, window.innerHeight);
            });
        </script></body></html>
        """.trimIndent()
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx -> 
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                webViewClient = WebViewClient()
                setOnTouchListener { v, ev -> 
                    when (ev.actionMasked) { 
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false) 
                    }
                    false 
                }
            }
        },
        update = { webView ->
            if (webView.contentDescription != html) {
                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                webView.contentDescription = html
            }
        }
    )
}

private fun placeholderSvgBase64(text: String): String {
    val svg = "<svg xmlns='http://www.w3.org/2000/svg' width='856' height='540'><rect width='100%' height='100%' fill='#DDDDDD'/><text x='50%' y='50%' dominant-baseline='middle' text-anchor='middle' fill='#666' font-size='42'>$text</text></svg>"
    return "data:image/svg+xml;base64," + Base64.encodeToString(svg.toByteArray(), Base64.NO_WRAP)
}

@Composable
fun FullscreenImageViewer(uri: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
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
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            if (scale == 1f) {
                                swipeOffsetY += dragAmount.y
                            }
                        },
                        onDragEnd = {
                            if (swipeOffsetY > 300) onClose() else swipeOffsetY = 0f
                        }
                    )
                }
        ) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, swipeOffsetY.roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                        alpha = (1f - (swipeOffsetY / 600f)).coerceIn(0.1f, 1f)
                    }
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2f
                                offsetX = 0f; offsetY = 0f
                            }
                        )
                    }
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}
