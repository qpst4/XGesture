package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.icon.AppIconTheme
import com.slideindex.app.ui.miuix.effect.BgEffectBackground
import com.slideindex.app.ui.navigation.NavPermissionSnapshot
import com.slideindex.app.ui.onboarding.OnboardingPracticeContent
import com.slideindex.app.ui.onboarding.OnboardingPracticeController
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class OnboardingStep {
    WELCOME,
    DISCLAIMER,
    PERMISSIONS,

    /** 权限之后的"上手两件事"：边缘触钮 → 悬浮指针、悬浮球取词。 */
    PRACTICE,
}

@Composable
fun OnboardingScreen(
    permissions: NavPermissionSnapshot,
    settingsRepository: SettingsRepository,
    onPreviewTriggerBar: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
    onComplete: () -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    val shaderSupported = remember(isDark) { isRuntimeShaderSupported() }

    val fallbackGradient = remember(isDark) {
        Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color(0xFF1E1B4B),
                    Color(0xFF0F172A),
                    Color(0xFF18181B),
                )
            } else {
                listOf(
                    Color(0xFFFFEEF4),
                    Color(0xFFEDE9FE),
                    Color(0xFFDBEAFE),
                )
            },
            start = Offset.Zero,
            end = Offset(1000f, 2000f),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fallbackGradient),
    ) {
        BgEffectBackground(
            dynamicBackground = shaderSupported,
            isFullSize = true,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Background decorative Origami Plane at middle-right edge
            DecorativePaperPlane(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(46.dp),
                isDark = isDark,
            )

            OnboardingMainFlow(
                permissions = permissions,
                settingsRepository = settingsRepository,
                onPreviewTriggerBar = onPreviewTriggerBar,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
                onRequestNotification = onRequestNotification,
                onComplete = onComplete,
            )
        }
    }
}

@Composable
private fun OnboardingMainFlow(
    permissions: NavPermissionSnapshot,
    settingsRepository: SettingsRepository,
    onPreviewTriggerBar: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
    onComplete: () -> Unit,
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var isDisclaimerAgreed by remember { mutableStateOf(false) }
    val practiceScope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    // 练习状态只活在这一次引导里（不落盘）：绑定与开关本身已经是持久设置，无需再存进度。
    val practiceController = remember(settingsRepository, practiceScope) {
        OnboardingPracticeController(
            context = appContext,
            settingsRepository = settingsRepository,
            scope = practiceScope,
            onPreviewTriggerBar = onPreviewTriggerBar,
        )
    }
    DisposableEffect(practiceController) {
        onDispose { practiceController.release() }
    }

    // Intercept system back button: navigate backwards smoothly
    BackHandler(enabled = currentStep != OnboardingStep.WELCOME) {
        currentStep = when (currentStep) {
            OnboardingStep.PRACTICE -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS -> OnboardingStep.DISCLAIMER
            OnboardingStep.DISCLAIMER -> OnboardingStep.WELCOME
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME
        }
    }

    val insetsPadding = WindowInsets.systemBars
        .add(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Vertical + WindowInsetsSides.Horizontal)
        .asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(insetsPadding),
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                // Gentle, luxurious page transition spec
                val pageSpringSpec = spring<Float>(
                    dampingRatio = 0.88f,
                    stiffness = 180f,
                )
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 180f),
                    ) { (it * 0.9f).toInt() } + fadeIn(tween(380, easing = FastOutSlowInEasing))).togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 180f),
                        ) { -(it * 0.45f).toInt() } + fadeOut(tween(300)),
                    )
                } else {
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 180f),
                    ) { -(it * 0.9f).toInt() } + fadeIn(tween(380, easing = FastOutSlowInEasing))).togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 180f),
                        ) { (it * 0.45f).toInt() } + fadeOut(tween(300)),
                    )
                }
            },
            label = "OnboardingStepTransition",
            modifier = Modifier.fillMaxSize(),
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> {
                    WelcomeScreenPage(
                        onNext = { currentStep = OnboardingStep.DISCLAIMER },
                    )
                }

                OnboardingStep.DISCLAIMER -> {
                    DisclaimerScreenPage(
                        isAgreed = isDisclaimerAgreed,
                        onAgreeChange = { isDisclaimerAgreed = it },
                        onBack = { currentStep = OnboardingStep.WELCOME },
                        onNext = { currentStep = OnboardingStep.PERMISSIONS },
                    )
                }

                OnboardingStep.PERMISSIONS -> {
                    PermissionsScreenPage(
                        permissions = permissions,
                        onRequestOverlay = onRequestOverlay,
                        onRequestAccessibility = onRequestAccessibility,
                        onRequestNotification = onRequestNotification,
                        onBack = { currentStep = OnboardingStep.DISCLAIMER },
                        // 权限之后还有"上手两件事"，所以这里不再是"进入应用"。
                        onComplete = { currentStep = OnboardingStep.PRACTICE },
                    )
                }

                OnboardingStep.PRACTICE -> {
                    OnboardingPracticeContent(
                        controller = practiceController,
                        onRequestAccessibility = onRequestAccessibility,
                        bottomBar = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Button(
                                    onClick = { currentStep = OnboardingStep.PERMISSIONS },
                                    cornerRadius = 18.dp,
                                    colors = ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.surfaceVariant,
                                        contentColor = MiuixTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.onboarding_back),
                                        style = MiuixTheme.textStyles.button,
                                    )
                                }

                                Button(
                                    onClick = {
                                        practiceController.release()
                                        onComplete()
                                    },
                                    cornerRadius = 18.dp,
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.onboarding_enter_app),
                                        style = MiuixTheme.textStyles.button,
                                        color = MiuixTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Step 1: Welcome page (App Logo in upper-middle + App Title + Subtitle + Elevated Floating Forward Button)
 */
@Composable
private fun WelcomeScreenPage(
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val selectedIconTheme = remember(context) { AppIconTheme.getSelected(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1.0f))

        // Staggered Spring Animation for App Logo (Gentle entrance)
        StaggeredEntrance(
            delayMillis = 60,
            initialOffsetY = (-24).dp,
            initialScale = 0.86f,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = Color(0x2A000000),
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(selectedIconTheme.iconRes),
                    contentDescription = stringResource(R.string.cd_app_icon),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Staggered Spring Animation for Title & Subtitle
        StaggeredEntrance(
            delayMillis = 150,
            initialOffsetY = 24.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.title1.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.onboarding_welcome_body),
                    style = MiuixTheme.textStyles.body2.copy(
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1.6f))

        // Staggered Spring Animation for Floating Action Button (Elevated position)
        StaggeredEntrance(
            delayMillis = 240,
            initialOffsetY = 28.dp,
            initialScale = 0.80f,
        ) {
            Button(
                onClick = onNext,
                cornerRadius = 32.dp,
                colors = ButtonDefaults.buttonColors(
                    color = Color.White.copy(alpha = 0.92f),
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
                insideMargin = PaddingValues(0.dp),
                modifier = Modifier
                    .size(62.dp)
                    .shadow(10.dp, CircleShape, spotColor = Color(0x22000000)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.onboarding_next),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Substantial bottom margin so button floats comfortably higher
        Spacer(modifier = Modifier.height(110.dp))
    }
}

/**
 * Step 2: Disclaimer page (Top 4-Facet Geometric Shield + Card with Terms + Checkbox + Bottom Buttons)
 */
@Composable
private fun DisclaimerScreenPage(
    isAgreed: Boolean,
    onAgreeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    // 内容区可滚动：小屏 / "显示大小"或字体放大时靠滚动兜底。
    // 底部按钮独立占位（50dp），不再和卡片抢高度，否则矮屏上会被压成 0 高。
    val contentScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 可滚动内容区：内容不足时居中，超出时可滚动
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(contentScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Staggered Entrance for 4-Facet Shield Illustration
                StaggeredEntrance(
                    delayMillis = 60,
                    initialOffsetY = (-24).dp,
                    initialScale = 0.86f,
                ) {
                    BoxProxyFacetShield(
                        modifier = Modifier.size(108.dp),
                        palette = if (isDark) {
                            FacetShieldPalette(
                                topLeft = Color(0xFFA898DE),
                                topRight = Color(0xFF7F67BD),
                                bottomLeft = Color(0xFF7F67BD),
                                bottomRight = Color(0xFF5E45A0),
                            )
                        } else {
                            FacetShieldPalette(
                                topLeft = Color(0xFFC4B6EE),
                                topRight = Color(0xFF9881D4),
                                bottomLeft = Color(0xFF9881D4),
                                bottomRight = Color(0xFF7256B8),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Staggered Entrance for Floating Terms Card
                StaggeredEntrance(
                    delayMillis = 150,
                    initialOffsetY = 32.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Card(
                        cornerRadius = 24.dp,
                        insideMargin = PaddingValues(22.dp),
                        colors = CardDefaults.defaultColors(
                            color = if (isDark) Color(0xFF1E1E24).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(24.dp),
                                spotColor = Color(0x16000000),
                            ),
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.onboarding_disclaimer_title),
                                style = MiuixTheme.textStyles.title1.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MiuixTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.onboarding_disclaimer_body),
                                style = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                lineHeight = 22.sp,
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Agreement Checkbox Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onAgreeChange(!isAgreed) },
                            ) {
                                Checkbox(
                                    state = ToggleableState(isAgreed),
                                    onClick = { onAgreeChange(!isAgreed) },
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_disclaimer_agree),
                                    style = MiuixTheme.textStyles.body1.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                    ),
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Staggered Entrance for Bottom Action Buttons
        StaggeredEntrance(
            delayMillis = 240,
            initialOffsetY = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = onBack,
                    cornerRadius = 18.dp,
                    colors = ButtonDefaults.buttonColors(
                        color = if (isDark) Color(0xFF2E2E36) else Color.White.copy(alpha = 0.88f),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MiuixTheme.textStyles.button,
                    )
                }

                Button(
                    onClick = onNext,
                    enabled = isAgreed,
                    cornerRadius = 18.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_next),
                        style = MiuixTheme.textStyles.button,
                        color = if (isAgreed) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.disabledOnPrimaryButton,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Step 3: Permissions page (Top Purple Shield with Avatar Badge + Card with 4 Permissions list + Bottom Buttons)
 */
@Composable
private fun PermissionsScreenPage(
    permissions: NavPermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalAppDarkTheme.current
    // 同免责声明页：内容区可滚动，底部按钮固定占位，避免矮屏上按钮被挤没。
    val contentScrollState = rememberScrollState()

    var isAppListGranted by remember { mutableStateOf(PermissionHelper.hasAppListPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAppListGranted = PermissionHelper.hasAppListPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 可滚动内容区：内容不足时居中，超出时可滚动
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(contentScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Staggered Entrance for Shield Illustration with Avatar Badge
                StaggeredEntrance(
                    delayMillis = 60,
                    initialOffsetY = (-24).dp,
                    initialScale = 0.86f,
                ) {
                    Box(
                        modifier = Modifier.size(108.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxProxyFacetShield(
                            modifier = Modifier.fillMaxSize(),
                            palette = if (isDark) {
                                FacetShieldPalette(
                                    topLeft = Color(0xFFA898DE),
                                    topRight = Color(0xFF7F67BD),
                                    bottomLeft = Color(0xFF7F67BD),
                                    bottomRight = Color(0xFF5E45A0),
                                )
                            } else {
                                FacetShieldPalette(
                                    topLeft = Color(0xFFC4B6EE),
                                    topRight = Color(0xFF9881D4),
                                    bottomLeft = Color(0xFF9881D4),
                                    bottomRight = Color(0xFF7256B8),
                                )
                            },
                        )

                        // Bottom-Right User Avatar Badge with White Border
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(44.dp)
                                .shadow(6.dp, CircleShape, spotColor = Color(0x28000000))
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF9881D4), Color(0xFF7256B8)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Staggered Entrance for Floating Card
                StaggeredEntrance(
                    delayMillis = 150,
                    initialOffsetY = 32.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Card(
                        cornerRadius = 24.dp,
                        insideMargin = PaddingValues(22.dp),
                        colors = CardDefaults.defaultColors(
                            color = if (isDark) Color(0xFF1E1E24).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(24.dp),
                                spotColor = Color(0x16000000),
                            ),
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.onboarding_core_permissions_title),
                                style = MiuixTheme.textStyles.title1.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MiuixTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.onboarding_core_permissions_body),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                lineHeight = 20.sp,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Permission item 1: Overlay
                            PermissionStatusRow(
                                title = stringResource(R.string.onboarding_overlay_title),
                                description = stringResource(R.string.onboarding_permission_overlay_short),
                                isGranted = permissions.overlayGranted,
                                onRequest = onRequestOverlay,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Permission item 2: Accessibility
                            PermissionStatusRow(
                                title = stringResource(R.string.onboarding_accessibility_title),
                                description = stringResource(R.string.onboarding_permission_accessibility_short),
                                isGranted = permissions.accessibilityGranted,
                                onRequest = onRequestAccessibility,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Permission item 3: Query Installed Apps
                            PermissionStatusRow(
                                title = stringResource(R.string.onboarding_permission_app_list_title),
                                description = stringResource(R.string.onboarding_permission_app_list_short),
                                isGranted = isAppListGranted,
                                onRequest = {
                                    PermissionHelper.requestAppListPermission(context)
                                    isAppListGranted = PermissionHelper.hasAppListPermission(context)
                                },
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Permission item 4: Notification
                            PermissionStatusRow(
                                title = stringResource(R.string.onboarding_permission_notification_title),
                                description = stringResource(R.string.onboarding_permission_notification_short),
                                isGranted = permissions.notificationGranted,
                                onRequest = onRequestNotification,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Staggered Entrance for Bottom Action Buttons
        StaggeredEntrance(
            delayMillis = 240,
            initialOffsetY = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = onBack,
                    cornerRadius = 18.dp,
                    colors = ButtonDefaults.buttonColors(
                        color = if (isDark) Color(0xFF2E2E36) else Color.White.copy(alpha = 0.88f),
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_back),
                        style = MiuixTheme.textStyles.button,
                    )
                }

                Button(
                    onClick = onComplete,
                    cornerRadius = 18.dp,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_next),
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Individual permission item inside the permission hub card
 */
@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isGranted) Color(0x1222C55E) else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp),
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                lineHeight = 15.sp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_permission_status_ready),
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.onboarding_permission_status_ready),
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = Color(0xFF22C55E),
                )
            }
        } else {
            TextButton(
                text = stringResource(R.string.onboarding_permission_status_grant),
                onClick = onRequest,
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * Reusable staggered entrance container with relaxed, graceful spring physics & alpha fade
 */
@Composable
private fun StaggeredEntrance(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    initialOffsetY: Dp = 24.dp,
    initialScale: Float = 0.92f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val alphaAnim = remember { Animatable(0f) }
    val offsetAnim = remember { Animatable(with(density) { initialOffsetY.toPx() }) }
    val scaleAnim = remember { Animatable(initialScale) }

    LaunchedEffect(Unit) {
        if (delayMillis > 0) {
            delay(delayMillis.toLong())
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            )
        }
        launch {
            offsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 160f,
                ),
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 160f,
                ),
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = alphaAnim.value
            translationY = offsetAnim.value
            scaleX = scaleAnim.value
            scaleY = scaleAnim.value
        },
    ) {
        content()
    }
}

private data class FacetShieldPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color,
)

/**
 * Exact Geometric 4-Quadrant Shield Illustration matching BoxProxy reference
 */
@Composable
private fun BoxProxyFacetShield(
    modifier: Modifier = Modifier,
    palette: FacetShieldPalette,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val midY = h * 0.44f
        val topR = w * 0.14f

        // Top-Left Quadrant
        val pTL = Path().apply {
            moveTo(cx, 0f)
            lineTo(topR, 0f)
            quadraticTo(0f, 0f, 0f, topR)
            lineTo(0f, midY)
            lineTo(cx, midY)
            close()
        }
        drawPath(pTL, color = palette.topLeft)

        // Top-Right Quadrant
        val pTR = Path().apply {
            moveTo(cx, 0f)
            lineTo(w - topR, 0f)
            quadraticTo(w, 0f, w, topR)
            lineTo(w, midY)
            lineTo(cx, midY)
            close()
        }
        drawPath(pTR, color = palette.topRight)

        // Bottom-Left Quadrant (Smoothly tapered to bottom tip)
        val pBL = Path().apply {
            moveTo(0f, midY)
            cubicTo(0f, h * 0.72f, cx * 0.5f, h * 0.92f, cx, h)
            lineTo(cx, midY)
            close()
        }
        drawPath(pBL, color = palette.bottomLeft)

        // Bottom-Right Quadrant (Smoothly tapered to bottom tip)
        val pBR = Path().apply {
            moveTo(w, midY)
            cubicTo(w, h * 0.72f, w - (cx * 0.5f), h * 0.92f, cx, h)
            lineTo(cx, midY)
            close()
        }
        drawPath(pBR, color = palette.bottomRight)
    }
}

/**
 * Subtle decorative Origami Paper Plane outline in background
 */
@Composable
private fun DecorativePaperPlane(
    modifier: Modifier = Modifier,
    isDark: Boolean,
) {
    val planeColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF6366F1).copy(alpha = 0.28f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.95f, h * 0.05f)
            lineTo(w * 0.05f, h * 0.55f)
            lineTo(w * 0.45f, h * 0.65f)
            lineTo(w * 0.65f, h * 0.95f)
            lineTo(w * 0.95f, h * 0.05f)
            close()

            moveTo(w * 0.95f, h * 0.05f)
            lineTo(w * 0.45f, h * 0.65f)
        }

        drawPath(
            path = path,
            color = planeColor,
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
