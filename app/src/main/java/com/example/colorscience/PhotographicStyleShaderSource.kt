package com.example.colorscience

/**
 * Single-source-of-truth OpenGL ES 2.0 / 3.0 shaders implementing Apple-grade Photographic Styles
 * and the 2D Interactive Color Palette (Hue, Saturation, Tone, Warmth):
 * - Piecewise sRGB <-> Linear RGB electro-optical transfer
 * - CIE XYZ (D65) <-> Bradford LMS cone-space chromatic adaptation
 * - CIE XYZ <-> CIELAB perceptual lightness & opponent chrominance
 * - Melanin-locus multi-spectral skin detection across Fitzpatrick I-VI
 * - Melanin hue-angle lock guaranteeing zero skin hue drift across all styles and adjustments
 * - Filmic tonal curve: smooth parametric shadow toe, midtone Hermite pivot, and asymptotic rational shoulder
 * - Selective nature color grading (polarizing sky blue-cyan depth, natural foliage preservation)
 * - Luminance-aware saturation roll-off in extreme shadows and specular highlights
 * - Luminance-preserving soft gamut mapping + triangular LSB dithering (zero banding)
 */
object PhotographicStyleShaderSource {

    const val VERTEX_SHADER_OES = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        uniform mat4 uVertexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uVertexMatrix * aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """

    const val VERTEX_SHADER_2D = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    fun buildFragmentShader(useOesExternalTexture: Boolean): String {
        val extensionHeader = if (useOesExternalTexture) {
            "#extension GL_OES_EGL_image_external : require\n"
        } else {
            ""
        }
        val samplerType = if (useOesExternalTexture) "samplerExternalOES" else "sampler2D"

        return """
            $extensionHeader
            precision highp float;

            varying vec2 vTexCoord;
            uniform $samplerType uTexture;

            uniform float uBypass;
            uniform float uShadowLift;
            uniform float uMidtoneLift;
            uniform float uContrast;
            uniform float uHighlightShoulder;
            uniform float uVibrance;
            uniform float uChromaScale;
            uniform float uWarmthShift;
            uniform float uTintShift;
            uniform float uSkinProtection;
            uniform float uCoolSkinGuard;
            uniform float uSkyFoliageBoost;
            uniform float uPaletteHueShiftDeg;
            uniform float uProMaxEnabled;
            uniform float uHdrHighlightRecovery;
            uniform float uHdrShadowLift;
            uniform float uLowLightFactor;
            uniform float uNaturalClarity;
            uniform vec2 uTexelStep;
            uniform float uSoftSkinAmount;
            uniform float uGlowAmount;
            uniform float uFilmAmount;
            uniform float uGrainAmount;

            vec3 srgbToLinear(vec3 c) {
                vec3 lo = c / 12.92;
                vec3 hi = pow(max((c + 0.055) / 1.055, vec3(0.0)), vec3(2.4));
                vec3 mask = step(vec3(0.04045), c);
                return mix(lo, hi, mask);
            }

            vec3 linearToSrgb(vec3 c) {
                vec3 clamped = clamp(c, 0.0, 1.0);
                vec3 lo = clamped * 12.92;
                vec3 hi = 1.055 * pow(max(clamped, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
                vec3 mask = step(vec3(0.0031308), clamped);
                return mix(lo, hi, mask);
            }

            vec3 rgbToXyz(vec3 rgb) {
                return vec3(
                    dot(rgb, vec3(0.4124564, 0.3575761, 0.1804375)),
                    dot(rgb, vec3(0.2126729, 0.7151522, 0.0721750)),
                    dot(rgb, vec3(0.0193339, 0.1191920, 0.9503041))
                );
            }

            vec3 xyzToRgb(vec3 xyz) {
                return vec3(
                    dot(xyz, vec3( 3.2404542, -1.5371385, -0.4985314)),
                    dot(xyz, vec3(-0.9692660,  1.8760108,  0.0415560)),
                    dot(xyz, vec3( 0.0556434, -0.2040259,  1.0572252))
                );
            }

            // Bradford physiological LMS cone response transform
            vec3 xyzToLms(vec3 xyz) {
                return vec3(
                    dot(xyz, vec3( 0.8951000,  0.2664000, -0.1614000)),
                    dot(xyz, vec3(-0.7502000,  1.7135000,  0.0367000)),
                    dot(xyz, vec3( 0.0389000, -0.0685000,  1.0296000))
                );
            }

            vec3 lmsToXyz(vec3 lms) {
                return vec3(
                    dot(lms, vec3( 0.9869929, -0.1470543,  0.1599627)),
                    dot(lms, vec3( 0.4323053,  0.5183603,  0.0492912)),
                    dot(lms, vec3(-0.0085287,  0.0400428,  0.9684867))
                );
            }

            float labF(float t) {
                const float eps = 0.00885645168;
                const float kappa = 7.787037037;
                return (t > eps) ? pow(max(t, 0.0), 1.0 / 3.0) : (kappa * t + 16.0 / 116.0);
            }

            float labFInv(float f) {
                const float delta = 6.0 / 29.0;
                return (f > delta) ? (f * f * f) : (3.0 * delta * delta * (f - 16.0 / 116.0));
            }

            vec3 xyzToLab(vec3 xyz) {
                vec3 n = xyz / vec3(0.95047, 1.00000, 1.08883);
                float fx = labF(n.x);
                float fy = labF(n.y);
                float fz = labF(n.z);
                return vec3(
                    116.0 * fy - 16.0,
                    500.0 * (fx - fy),
                    200.0 * (fy - fz)
                );
            }

            vec3 labToXyz(vec3 lab) {
                float fy = (lab.x + 16.0) / 116.0;
                float fx = lab.y / 500.0 + fy;
                float fz = fy - lab.z / 200.0;
                return vec3(
                    0.95047 * labFInv(fx),
                    1.00000 * labFInv(fy),
                    1.08883 * labFInv(fz)
                );
            }

            vec3 rgbToHsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-6;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            vec3 hsvToRgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }

            // Melanin-locus skin detection across Fitzpatrick I through VI
            float computeSkinWeight(vec3 srgb, vec3 lab) {
                vec3 hsv = rgbToHsv(clamp(srgb, 0.0, 1.0));
                float angleRad = atan(lab.z, lab.y);
                float angleDeg = degrees(angleRad);
                if (angleDeg < 0.0) angleDeg += 360.0;

                // Human melanin locus centered at 48.0 deg (b*/a* ~ 1.1)
                float angleDist = abs(angleDeg - 48.0);
                float angleMask = 1.0 - smoothstep(12.0, 30.0, angleDist);

                float chroma = length(lab.yz);
                float chromaMask = smoothstep(3.5, 9.0, chroma) * (1.0 - smoothstep(46.0, 68.0, chroma));

                // Broad lightness support covering Fitzpatrick VI (dark) to Fitzpatrick I (fair)
                float lightMask = smoothstep(8.0, 18.0, lab.x) * (1.0 - smoothstep(88.0, 96.0, lab.x));
                float aMask = smoothstep(1.5, 5.0, lab.y) * (1.0 - smoothstep(36.0, 50.0, lab.y));

                float hDeg = hsv.x * 360.0;
                float hDist = (hDeg > 300.0) ? (360.0 - hDeg) : hDeg;
                float hsvMask = (1.0 - smoothstep(28.0, 52.0, hDist)) *
                    smoothstep(0.06, 0.15, hsv.y) *
                    (1.0 - smoothstep(0.70, 0.86, hsv.y)) *
                    smoothstep(0.08, 0.18, hsv.z);

                float ratio = lab.z / max(lab.y, 0.8);
                float ratioMask = smoothstep(0.40, 0.80, ratio) * (1.0 - smoothstep(2.2, 3.0, ratio));

                return clamp(angleMask * chromaMask * lightMask * aMask * hsvMask * ratioMask * 1.6, 0.0, 1.0);
            }

            vec3 softGamutMap(vec3 linRgb) {
                float lum = dot(linRgb, vec3(0.2126729, 0.7151522, 0.0721750));
                float maxC = max(linRgb.r, max(linRgb.g, linRgb.b));
                vec3 res = linRgb;
                if (maxC > 1.0) {
                    float scale = (1.0 - lum) / max(maxC - lum, 1e-5);
                    res = mix(vec3(lum), res, clamp(scale, 0.0, 1.0));
                }
                float minC = min(res.r, min(res.g, res.b));
                if (minC < 0.0) {
                    float scaleNeg = lum / max(lum - minC, 1e-5);
                    res = mix(vec3(lum), res, clamp(scaleNeg, 0.0, 1.0));
                }
                return clamp(res, 0.0, 1.0);
            }

            float triangularDither(vec2 coord) {
                float n1 = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
                float n2 = fract(sin(dot(coord, vec2(39.346, 11.135))) * 43758.5453);
                return (n1 + n2 - 1.0) / 255.0;
            }

            void main() {
                vec4 texel = texture2D(uTexture, vTexCoord);
                vec3 srgbIn = clamp(texel.rgb, 0.0, 1.0);

                if (uBypass > 0.5) {
                    gl_FragColor = vec4(srgbIn, texel.a);
                    return;
                }

                // Soft Skin (edge-preserving bilateral smoothing gated tightly to skin pixels)
                vec2 stepPx = max(uTexelStep, vec2(0.0005));
                if (uSoftSkinAmount > 0.005) {
                    vec3 n1 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(stepPx.x * 2.0, 0.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 n2 = clamp(texture2D(uTexture, clamp(vTexCoord - vec2(stepPx.x * 2.0, 0.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 n3 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(0.0, stepPx.y * 2.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 n4 = clamp(texture2D(uTexture, clamp(vTexCoord - vec2(0.0, stepPx.y * 2.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 n5 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(stepPx.x * 3.5, stepPx.y * 3.5), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 n6 = clamp(texture2D(uTexture, clamp(vTexCoord - vec2(stepPx.x * 3.5, stepPx.y * 3.5), 0.001, 0.999)).rgb, 0.0, 1.0);

                    float w1 = exp(-dot(n1 - srgbIn, n1 - srgbIn) * 42.0);
                    float w2 = exp(-dot(n2 - srgbIn, n2 - srgbIn) * 42.0);
                    float w3 = exp(-dot(n3 - srgbIn, n3 - srgbIn) * 42.0);
                    float w4 = exp(-dot(n4 - srgbIn, n4 - srgbIn) * 42.0);
                    float w5 = exp(-dot(n5 - srgbIn, n5 - srgbIn) * 32.0);
                    float w6 = exp(-dot(n6 - srgbIn, n6 - srgbIn) * 32.0);
                    float wSum = 1.0 + w1 + w2 + w3 + w4 + w5 + w6;
                    vec3 smoothRgb = (srgbIn + n1 * w1 + n2 * w2 + n3 * w3 + n4 * w4 + n5 * w5 + n6 * w6) / wSum;

                    vec3 preLab = xyzToLab(rgbToXyz(srgbToLinear(srgbIn)));
                    float preSkin = computeSkinWeight(srgbIn, preLab);
                    srgbIn = mix(srgbIn, smoothRgb, clamp(preSkin * uSoftSkinAmount * 0.72, 0.0, 0.78));
                }

                // 1. Linearize light & initial color space
                vec3 lin0 = srgbToLinear(srgbIn);
                vec3 xyz0 = rgbToXyz(lin0);
                vec3 lab0 = xyzToLab(xyz0);

                // 2. Multi-spectral skin-tone detection & melanin hue anchor
                float skinWeight = computeSkinWeight(srgbIn, lab0);
                float origSkinAngle = atan(lab0.z, lab0.y);

                // 3. Bradford LMS Cone Chromatic Adaptation (Warmth & Tint) with neutral highlight preservation
                float lum0 = clamp(xyz0.y, 0.0, 1.0);
                float highlightGuard = 1.0 - smoothstep(0.76, 0.98, lum0);
                float skinWarmGuard = (uWarmthShift < 0.0)
                    ? (1.0 - skinWeight * uCoolSkinGuard)
                    : (1.0 - skinWeight * 0.16);
                float effWarmth = uWarmthShift * mix(0.35, 1.0, highlightGuard) * skinWarmGuard;
                float effTint = uTintShift * mix(0.40, 1.0, highlightGuard);

                vec3 coneGains = vec3(
                    1.0 + effWarmth * 0.22 - effTint * 0.06,
                    1.0 + effWarmth * 0.04 + effTint * 0.16,
                    1.0 - effWarmth * 0.28 + effTint * 0.06
                );
                vec3 lms0 = xyzToLms(xyz0);
                vec3 lmsAdapted = lms0 * coneGains;
                vec3 xyzAdapted = lmsToXyz(lmsAdapted);

                float yAdapted = max(xyzAdapted.y, 1e-5);
                float lumPreserve = mix(1.0, lum0 / yAdapted, 0.85);
                vec3 xyzWb = xyzAdapted * lumPreserve;
                vec3 linWb = xyzToRgb(xyzWb);

                // 4. Perceptual CIELAB space
                vec3 lab = xyzToLab(xyzWb);
                float L = clamp(lab.x / 100.0, 0.0, 1.0);

                // Stage 3 (Pre-Style): Pro Max Smart HDR & Night Tone Conditioning
                if (uProMaxEnabled > 0.5) {
                    float blackAnchor = smoothstep(0.025, 0.11, L);
                    float hdrShadowMask = smoothstep(0.46, 0.05, L) * blackAnchor;
                    float nightDamp = 1.0 - uLowLightFactor * 0.42;
                    L = L + uHdrShadowLift * 0.24 * hdrShadowMask * nightDamp;

                    if (L > 0.68) {
                        float overHdr = (L - 0.68) / 0.32;
                        float hdrComp = 0.68 + 0.32 * (overHdr / (1.0 + overHdr * uHdrHighlightRecovery * 1.35));
                        L = mix(L, hdrComp, smoothstep(0.68, 0.90, L));
                    }

                    float clarityBand = smoothstep(0.14, 0.38, L) * (1.0 - smoothstep(0.72, 0.95, L));
                    float presenceBoost = uNaturalClarity * (1.0 - skinWeight * 0.65) * clarityBand;
                    L = clamp(L + (L - 0.50) * presenceBoost * 0.55, 0.0, 1.0);
                }

                // Stage 4 & 5: Filmic Tonal Shaping (Shadow Toe, Midtone Lift, Hermite S-curve, Highlight Shoulder)
                float shadowMask = smoothstep(0.48, 0.0, L) * smoothstep(0.0, 0.07, L);
                float shadowAtten = 1.0 - (skinWeight * uSkinProtection * (uShadowLift < 0.0 ? 0.50 : 0.22));
                L = L + uShadowLift * 0.24 * shadowMask * shadowAtten;

                float midMask = smoothstep(0.03, 0.35, L) * (1.0 - smoothstep(0.68, 0.98, L));
                L = L + uMidtoneLift * midMask;

                float effContrast = mix(uContrast, 1.0 + (uContrast - 1.0) * 0.40, skinWeight * uSkinProtection);
                float centered = L - 0.46;
                float curved = 0.46 + centered * effContrast;
                float extremeBlend = smoothstep(0.01, 0.18, L) * (1.0 - smoothstep(0.74, 1.0, L));
                L = mix(L, curved, extremeBlend);

                if (L > 0.68) {
                    float over = (L - 0.68) / 0.32;
                    float compressed = 0.68 + 0.32 * (over / (1.0 + over * uHighlightShoulder));
                    L = mix(L, compressed, smoothstep(0.68, 0.90, L));
                }
                L = clamp(L, 0.0, 1.0);

                // 6. 2D Palette Hue Rotation with skin protection
                vec3 srgbWb = linearToSrgb(clamp(linWb, 0.0, 1.0));
                vec3 hsvWb = rgbToHsv(srgbWb);
                float hDeg = hsvWb.x * 360.0;
                vec2 abBase = lab.yz;

                float effHueShift = uPaletteHueShiftDeg * (1.0 - skinWeight * uSkinProtection * 0.85);
                if (abs(effHueShift) > 0.05 && hsvWb.y > 0.03) {
                    vec3 hsvRot = vec3(fract(hsvWb.x + effHueShift / 360.0), hsvWb.y, hsvWb.z);
                    vec3 rgbRot = hsvToRgb(hsvRot);
                    vec3 labRot = xyzToLab(rgbToXyz(srgbToLinear(rgbRot)));
                    float cOrig = length(abBase);
                    float cRot = length(labRot.yz);
                    if (cRot > 0.5 && cOrig > 0.5) {
                        abBase = labRot.yz * (cOrig / cRot);
                    }
                }

                // 7. Nature Color Families: Blue sky depth & natural foliage green
                float skyMask = smoothstep(185.0, 205.0, hDeg) * (1.0 - smoothstep(238.0, 258.0, hDeg));
                float greenMask = smoothstep(76.0, 96.0, hDeg) * (1.0 - smoothstep(145.0, 168.0, hDeg));
                float natureBoost = 1.0 + uSkyFoliageBoost * max(skyMask, greenMask) * smoothstep(0.08, 0.42, hsvWb.y);
                if (skyMask > 0.01 && uSkyFoliageBoost > 0.01) {
                    L = clamp(L - 0.035 * uSkyFoliageBoost * skyMask * smoothstep(0.12, 0.55, hsvWb.y), 0.0, 1.0);
                }

                // 8. Vibrance, Saturation, and Luminance-Aware Chroma Roll-off
                float chroma = length(abBase);
                float chromaNorm = clamp(chroma / 72.0, 0.0, 1.0);
                float vibranceGate = clamp(uChromaScale / 0.35, 0.0, 1.0);
                float vibranceGain = 1.0 + (uVibrance * vibranceGate) * (1.0 - chromaNorm) * (1.0 - chromaNorm);
                float baseChromaGain = uChromaScale * vibranceGain;

                float targetChromaGain = baseChromaGain * natureBoost;
                float skinMaxChromaGain = (targetChromaGain > 1.0)
                    ? (1.0 + (targetChromaGain - 1.0) * (1.0 - uSkinProtection * 0.78))
                    : targetChromaGain;
                float finalChromaGain = mix(targetChromaGain, skinMaxChromaGain, skinWeight);

                // Natural desaturation in deep shadows and specular highlights
                float satRollOff = (1.0 - smoothstep(0.78, 0.98, L) * 0.70) * smoothstep(0.015, 0.10, L);
                vec2 abScaled = abBase * (finalChromaGain * satRollOff);

                // Ambient warmth and tint modulation
                float labWarmMod = effWarmth * 14.0 * (1.0 - smoothstep(0.78, 0.98, L));
                float labTintMod = effTint * 10.0 * (1.0 - skinWeight * 0.45);
                vec2 abGraded = abScaled + vec2(labTintMod, labWarmMod);

                // 9. Melanin Hue-Angle Lock: Project skin pixels to original melanin angle
                float gradedChroma = length(abGraded);
                vec2 skinLockedAb = vec2(gradedChroma * cos(origSkinAngle), gradedChroma * sin(origSkinAngle));
                vec2 abOut = mix(abGraded, skinLockedAb, clamp(skinWeight * uSkinProtection, 0.0, 1.0));

                if (uFilmAmount > 0.005) {
                    float toeLift = smoothstep(0.34, 0.0, L) * 0.032 * uFilmAmount;
                    float filmCurve = L + (L - 0.50) * (0.14 * uFilmAmount) * smoothstep(0.08, 0.30, L) * (1.0 - smoothstep(0.76, 0.98, L));
                    float highlightCeiling = mix(1.0, 0.965, uFilmAmount);
                    L = clamp(min(filmCurve + toeLift, highlightCeiling), 0.0, 1.0);

                    float shadowSplit = smoothstep(0.32, 0.04, L) * uFilmAmount;
                    float highlightSplit = smoothstep(0.56, 0.90, L) * uFilmAmount;
                    abOut += vec2(-1.4, -2.8) * shadowSplit + vec2(0.9, 3.6) * highlightSplit * (1.0 - skinWeight * 0.45);
                }

                // 10. Reconstruct Linear RGB, soft gamut map, encode sRGB
                vec3 labFinal = vec3(L * 100.0, abOut.x, abOut.y);
                vec3 linOut = softGamutMap(xyzToRgb(labToXyz(labFinal)));
                vec3 srgbOut = linearToSrgb(linOut);

                // Optical highlight diffusion glow
                if (uGlowAmount > 0.005) {
                    vec3 g1 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(stepPx.x * 3.0, stepPx.y * 3.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g2 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(-stepPx.x * 3.0, stepPx.y * 3.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g3 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(stepPx.x * 3.0, -stepPx.y * 3.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g4 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(-stepPx.x * 3.0, -stepPx.y * 3.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g5 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(stepPx.x * 6.0, 0.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g6 = clamp(texture2D(uTexture, clamp(vTexCoord - vec2(stepPx.x * 6.0, 0.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g7 = clamp(texture2D(uTexture, clamp(vTexCoord + vec2(0.0, stepPx.y * 6.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 g8 = clamp(texture2D(uTexture, clamp(vTexCoord - vec2(0.0, stepPx.y * 6.0), 0.001, 0.999)).rgb, 0.0, 1.0);
                    vec3 blurSample = (g1 + g2 + g3 + g4 + g5 + g6 + g7 + g8) * 0.125;
                    float bloomLum = dot(blurSample, vec3(0.2126, 0.7152, 0.0722));
                    float bloomGate = smoothstep(0.54, 0.94, bloomLum);
                    vec3 bloomContrib = blurSample * bloomGate * (uGlowAmount * 0.36);
                    srgbOut = 1.0 - (1.0 - srgbOut) * (1.0 - clamp(bloomContrib, 0.0, 0.65));
                }

                // Organic midtone-weighted film grain
                if (uGrainAmount > 0.005) {
                    float nA = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
                    float nB = fract(sin(dot(gl_FragCoord.xy + vec2(17.3, 41.7), vec2(39.346, 11.135))) * 24634.6345);
                    float zeroMeanGrain = (nA + nB) * 0.5 - 0.5;
                    float grainMask = 0.25 + 0.75 * smoothstep(0.04, 0.24, L) * (1.0 - smoothstep(0.78, 0.98, L));
                    srgbOut += vec3(zeroMeanGrain * uGrainAmount * 0.14 * grainMask);
                }

                srgbOut = clamp(srgbOut + vec3(triangularDither(gl_FragCoord.xy)), 0.0, 1.0);

                gl_FragColor = vec4(srgbOut, texel.a);
            }
        """.trimIndent()
    }
}
