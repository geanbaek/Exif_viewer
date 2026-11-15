package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.model.CameraSummary
import com.example.model.ExifCategory
import com.example.model.ExifPhotoReport
import com.example.model.ExifTagEntry
import com.example.model.ExposureSummary
import com.example.model.LocationSummary
import com.example.model.TechnicalSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class ExifExtractor(private val context: Context) {

    private data class TagDescriptor(
        val tag: String,
        val displayName: String,
        val category: ExifCategory,
        val formatter: ((ExifInterface, String) -> String)? = null
    )

    suspend fun extract(uri: Uri): Result<ExifPhotoReport> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            val (fileName, fileSizeBytes) = queryFileMetadata(uri)

            // 1. Read pixel dimensions via BitmapFactory bounds
            var decodedWidth = 0
            var decodedHeight = 0
            contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    decodedWidth = opts.outWidth
                    decodedHeight = opts.outHeight
                }
            }

            // 2. Read EXIF via ExifInterface
            val exif = contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream)
            } ?: error("이미지 스트림을 열 수 없습니다.")

            val exifWidth = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)
                .takeIf { it > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val exifHeight = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)
                .takeIf { it > 0 } ?: exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)

            val width = if (decodedWidth > 0) decodedWidth else exifWidth
            val height = if (decodedHeight > 0) decodedHeight else exifHeight

            val megapixelsDisplay = if (width > 0 && height > 0) {
                val mp = (width.toDouble() * height.toDouble()) / 1_000_000.0
                String.format(Locale.US, "%.1f MP", mp)
            } else {
                null
            }

            val aspectRatioDisplay = if (width > 0 && height > 0) {
                val divisor = gcd(width, height)
                val rw = width / divisor
                val rh = height / divisor
                if (rw <= 32 && rh <= 32) {
                    "$rw:$rh"
                } else {
                    String.format(Locale.US, "%.2f:1", width.toDouble() / height.toDouble())
                }
            } else {
                null
            }

            val formatLabel = resolveFormatLabel(mimeType, fileName)

            val cameraSummary = CameraSummary(
                make = exif.cleanAttr(ExifInterface.TAG_MAKE),
                model = exif.cleanAttr(ExifInterface.TAG_MODEL),
                lensMake = exif.cleanAttr(ExifInterface.TAG_LENS_MAKE),
                lensModel = exif.cleanAttr(ExifInterface.TAG_LENS_MODEL),
                software = exif.cleanAttr(ExifInterface.TAG_SOFTWARE),
                artistOrOwner = exif.cleanAttr(ExifInterface.TAG_CAMERA_OWNER_NAME)
                    ?: exif.cleanAttr(ExifInterface.TAG_ARTIST),
                copyright = exif.cleanAttr(ExifInterface.TAG_COPYRIGHT)
            )

            val apertureDisplay = formatAperture(exif)
            val shutterDisplay = formatShutterSpeed(exif)
            val isoDisplay = formatIso(exif)
            val focalLengthDisplay = formatFocalLength(exif)
            val focalLength35Display = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                .takeIf { it > 0 }?.let { "${it}mm" }

            val exposureSummary = ExposureSummary(
                apertureDisplay = apertureDisplay,
                shutterSpeedDisplay = shutterDisplay,
                isoDisplay = isoDisplay,
                focalLengthDisplay = focalLengthDisplay,
                focalLength35mmDisplay = focalLength35Display,
                exposureBiasDisplay = formatExposureBias(exif),
                exposureProgram = exif.cleanAttr(ExifInterface.TAG_EXPOSURE_PROGRAM)
                    ?.let { decodeExposureProgram(it) },
                exposureMode = exif.cleanAttr(ExifInterface.TAG_EXPOSURE_MODE)
                    ?.let { decodeExposureMode(it) },
                meteringMode = exif.cleanAttr(ExifInterface.TAG_METERING_MODE)
                    ?.let { decodeMeteringMode(it) },
                whiteBalance = exif.cleanAttr(ExifInterface.TAG_WHITE_BALANCE)
                    ?.let { decodeWhiteBalance(it) },
                flash = exif.cleanAttr(ExifInterface.TAG_FLASH)
                    ?.let { decodeFlash(it) },
                lightSource = exif.cleanAttr(ExifInterface.TAG_LIGHT_SOURCE)
                    ?.let { decodeLightSource(it) },
                sceneCaptureType = exif.cleanAttr(ExifInterface.TAG_SCENE_CAPTURE_TYPE)
                    ?.let { decodeSceneCaptureType(it) }
            )

            val locationSummary = extractLocationSummary(exif)

            val xRes = exif.cleanAttr(ExifInterface.TAG_X_RESOLUTION)?.let { parseRationalOrDouble(it) }
            val yRes = exif.cleanAttr(ExifInterface.TAG_Y_RESOLUTION)?.let { parseRationalOrDouble(it) }
            val resUnit = when (exif.getAttributeInt(ExifInterface.TAG_RESOLUTION_UNIT, -1)) {
                2 -> "DPI"
                3 -> "dpcm"
                else -> "DPI"
            }
            val resolutionDisplay = when {
                xRes != null && yRes != null && abs(xRes - yRes) < 0.1 ->
                    "${xRes.roundToInt()} $resUnit"
                xRes != null && yRes != null ->
                    "${xRes.roundToInt()} × ${yRes.roundToInt()} $resUnit"
                else -> null
            }

            val technicalSummary = TechnicalSummary(
                orientationDisplay = exif.cleanAttr(ExifInterface.TAG_ORIENTATION)
                    ?.let { decodeOrientation(it) },
                colorSpaceDisplay = exif.cleanAttr(ExifInterface.TAG_COLOR_SPACE)
                    ?.let { decodeColorSpace(it) },
                resolutionDpiDisplay = resolutionDisplay,
                compressionDisplay = exif.cleanAttr(ExifInterface.TAG_COMPRESSION)
                    ?.let { decodeCompression(it) },
                bitsPerSampleDisplay = exif.cleanAttr(ExifInterface.TAG_BITS_PER_SAMPLE)
                    ?.let { "$it bit" },
                sensingMethodDisplay = exif.cleanAttr(ExifInterface.TAG_SENSING_METHOD)
                    ?.let { decodeSensingMethod(it) },
                customRenderedDisplay = exif.cleanAttr(ExifInterface.TAG_CUSTOM_RENDERED)
                    ?.let { decodeCustomRendered(it) }
            )

            val capturedDateDisplay = formatExifDateTime(
                exif.cleanAttr(ExifInterface.TAG_DATETIME_ORIGINAL),
                exif.cleanAttr(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                exif.cleanAttr(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
            )
            val digitizedDateDisplay = formatExifDateTime(
                exif.cleanAttr(ExifInterface.TAG_DATETIME_DIGITIZED),
                exif.cleanAttr(ExifInterface.TAG_OFFSET_TIME_DIGITIZED),
                exif.cleanAttr(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED)
            )
            val modifiedDateDisplay = formatExifDateTime(
                exif.cleanAttr(ExifInterface.TAG_DATETIME),
                exif.cleanAttr(ExifInterface.TAG_OFFSET_TIME),
                exif.cleanAttr(ExifInterface.TAG_SUBSEC_TIME)
            )

            val rawTags = extractAllTags(exif)

            ExifPhotoReport(
                uriString = uri.toString(),
                fileName = fileName ?: "선택한 이미지",
                fileSizeBytes = fileSizeBytes,
                fileSizeDisplay = fileSizeBytes?.let { formatFileSize(it) },
                mimeType = mimeType,
                formatLabel = formatLabel,
                imageWidth = width,
                imageHeight = height,
                megapixelsDisplay = megapixelsDisplay,
                aspectRatioDisplay = aspectRatioDisplay,
                capturedDateDisplay = capturedDateDisplay,
                digitizedDateDisplay = digitizedDateDisplay,
                modifiedDateDisplay = modifiedDateDisplay,
                cameraSummary = cameraSummary,
                exposureSummary = exposureSummary,
                locationSummary = locationSummary,
                technicalSummary = technicalSummary,
                rawTags = rawTags
            )
        }
    }

    private fun queryFileMetadata(uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx).takeIf { it > 0 }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to URI last path segment below
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name to size
    }

    private fun extractLocationSummary(exif: ExifInterface): LocationSummary? {
        val latLong = exif.latLong ?: return null
        val lat = latLong[0]
        val lon = latLong[1]
        if (lat.isNaN() || lon.isNaN()) return null

        val decimalDisplay = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        val dmsDisplay = "${toDms(lat, "N", "S")}  ${toDms(lon, "E", "W")}"

        val altitude = exif.getAltitude(Double.NaN)
        val altitudeDisplay = if (!altitude.isNaN()) {
            String.format(Locale.US, "%.1f m", altitude)
        } else {
            null
        }

        val gpsDate = exif.cleanAttr(ExifInterface.TAG_GPS_DATESTAMP)
        val gpsTime = exif.cleanAttr(ExifInterface.TAG_GPS_TIMESTAMP)
        val gpsDateTimeDisplay = listOfNotNull(gpsDate, gpsTime)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val directionRaw = exif.cleanAttr(ExifInterface.TAG_GPS_IMG_DIRECTION)
        val directionValue = directionRaw?.let { parseRationalOrDouble(it) }
        val directionDisplay = directionValue?.let {
            String.format(Locale.US, "%.1f°", it)
        }

        val processingMethod = exif.cleanAttr(ExifInterface.TAG_GPS_PROCESSING_METHOD)

        return LocationSummary(
            latitude = lat,
            longitude = lon,
            decimalDisplay = decimalDisplay,
            dmsDisplay = dmsDisplay,
            altitudeDisplay = altitudeDisplay,
            gpsDateTimeDisplay = gpsDateTimeDisplay,
            directionDisplay = directionDisplay,
            processingMethod = processingMethod
        )
    }

    private fun extractAllTags(exif: ExifInterface): List<ExifTagEntry> {
        return supportedTagDescriptors.mapNotNull { descriptor ->
            val raw = exif.cleanAttr(descriptor.tag) ?: return@mapNotNull null
            val formatted = descriptor.formatter?.invoke(exif, raw) ?: raw
            ExifTagEntry(
                tagKey = descriptor.tag,
                displayName = descriptor.displayName,
                rawValue = raw,
                formattedValue = formatted,
                category = descriptor.category
            )
        }
    }

    private val supportedTagDescriptors: List<TagDescriptor> = listOf(
        // Camera & Lens
        TagDescriptor(ExifInterface.TAG_MAKE, "제조사 (Make)", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_MODEL, "카메라 모델 (Model)", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_LENS_MAKE, "렌즈 제조사 (Lens Make)", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_LENS_MODEL, "렌즈 모델 (Lens Model)", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_LENS_SPECIFICATION, "렌즈 사양 (Lens Spec)", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_BODY_SERIAL_NUMBER, "바디 시리얼 번호", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_LENS_SERIAL_NUMBER, "렌즈 시리얼 번호", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_SOFTWARE, "소프트웨어 / 펌웨어", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_CAMERA_OWNER_NAME, "카메라 소유자", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_ARTIST, "촬영자 / 작가 (Artist)", ExifCategory.CAMERA),
        TagDescriptor(ExifInterface.TAG_COPYRIGHT, "저작권 (Copyright)", ExifCategory.CAMERA),

        // Exposure & Optics
        TagDescriptor(ExifInterface.TAG_F_NUMBER, "조리개 (F-Number)", ExifCategory.EXPOSURE) { ex, _ ->
            formatAperture(ex) ?: ex.cleanAttr(ExifInterface.TAG_F_NUMBER).orEmpty()
        },
        TagDescriptor(ExifInterface.TAG_APERTURE_VALUE, "조리개 APEX 값", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_MAX_APERTURE_VALUE, "렌즈 최대 개방 조리개", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_EXPOSURE_TIME, "셔터 속도 (Exposure Time)", ExifCategory.EXPOSURE) { ex, _ ->
            formatShutterSpeed(ex) ?: ex.cleanAttr(ExifInterface.TAG_EXPOSURE_TIME).orEmpty()
        },
        TagDescriptor(ExifInterface.TAG_SHUTTER_SPEED_VALUE, "셔터 속도 APEX 값", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "감도 (ISO)", ExifCategory.EXPOSURE) { _, raw ->
            "ISO $raw"
        },
        TagDescriptor(ExifInterface.TAG_ISO_SPEED, "ISO Speed", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX, "권장 노출 지수 (REI)", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_FOCAL_LENGTH, "초점 거리 (Focal Length)", ExifCategory.EXPOSURE) { ex, raw ->
            formatFocalLength(ex) ?: raw
        },
        TagDescriptor(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "35mm 환산 초점 거리", ExifCategory.EXPOSURE) { _, raw ->
            "${raw}mm"
        },
        TagDescriptor(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "디지털 줌 배율", ExifCategory.EXPOSURE) { _, raw ->
            val v = parseRationalOrDouble(raw)
            if (v != null) String.format(Locale.US, "%.2fx", v) else raw
        },
        TagDescriptor(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "노출 보정 (Exposure Bias)", ExifCategory.EXPOSURE) { ex, raw ->
            formatExposureBias(ex) ?: raw
        },
        TagDescriptor(ExifInterface.TAG_EXPOSURE_PROGRAM, "노출 프로그램", ExifCategory.EXPOSURE) { _, raw ->
            decodeExposureProgram(raw)
        },
        TagDescriptor(ExifInterface.TAG_EXPOSURE_MODE, "노출 모드", ExifCategory.EXPOSURE) { _, raw ->
            decodeExposureMode(raw)
        },
        TagDescriptor(ExifInterface.TAG_METERING_MODE, "측광 방식 (Metering)", ExifCategory.EXPOSURE) { _, raw ->
            decodeMeteringMode(raw)
        },
        TagDescriptor(ExifInterface.TAG_WHITE_BALANCE, "화이트 밸런스", ExifCategory.EXPOSURE) { _, raw ->
            decodeWhiteBalance(raw)
        },
        TagDescriptor(ExifInterface.TAG_FLASH, "플래시 (Flash)", ExifCategory.EXPOSURE) { _, raw ->
            decodeFlash(raw)
        },
        TagDescriptor(ExifInterface.TAG_LIGHT_SOURCE, "광원 (Light Source)", ExifCategory.EXPOSURE) { _, raw ->
            decodeLightSource(raw)
        },
        TagDescriptor(ExifInterface.TAG_BRIGHTNESS_VALUE, "밝기 값 (Brightness)", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_SCENE_CAPTURE_TYPE, "장면 캡처 유형", ExifCategory.EXPOSURE) { _, raw ->
            decodeSceneCaptureType(raw)
        },
        TagDescriptor(ExifInterface.TAG_SUBJECT_DISTANCE, "피사체 거리", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, "피사체 거리 범위", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_GAIN_CONTROL, "게인 컨트롤", ExifCategory.EXPOSURE),
        TagDescriptor(ExifInterface.TAG_CONTRAST, "대비 (Contrast)", ExifCategory.EXPOSURE) { _, raw ->
            decodeNormalSoftHard(raw)
        },
        TagDescriptor(ExifInterface.TAG_SATURATION, "채도 (Saturation)", ExifCategory.EXPOSURE) { _, raw ->
            decodeNormalLowHigh(raw)
        },
        TagDescriptor(ExifInterface.TAG_SHARPNESS, "선명도 (Sharpness)", ExifCategory.EXPOSURE) { _, raw ->
            decodeNormalSoftHard(raw)
        },

        // Date & Time
        TagDescriptor(ExifInterface.TAG_DATETIME_ORIGINAL, "원본 촬영 일시", ExifCategory.DATE_TIME),
        TagDescriptor(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "촬영 시간대 오프셋", ExifCategory.DATE_TIME),
        TagDescriptor(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, "촬영 밀리초 (Subsec)", ExifCategory.DATE_TIME),
        TagDescriptor(ExifInterface.TAG_DATETIME_DIGITIZED, "디지털화 일시", ExifCategory.DATE_TIME),
        TagDescriptor(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, "디지털화 시간대 오프셋", ExifCategory.DATE_TIME),
        TagDescriptor(ExifInterface.TAG_DATETIME, "파일 수정 일시", ExifCategory.DATE_TIME),
        TagDescriptor(ExifInterface.TAG_OFFSET_TIME, "수정 시간대 오프셋", ExifCategory.DATE_TIME),

        // Location / GPS
        TagDescriptor(ExifInterface.TAG_GPS_LATITUDE, "GPS 위도 (Latitude)", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_LATITUDE_REF, "GPS 위도 기준 (N/S)", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_LONGITUDE, "GPS 경도 (Longitude)", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_LONGITUDE_REF, "GPS 경도 기준 (E/W)", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_ALTITUDE, "GPS 고도 (Altitude)", ExifCategory.LOCATION) { ex, raw ->
            val alt = ex.getAltitude(Double.NaN)
            if (!alt.isNaN()) String.format(Locale.US, "%.1f m", alt) else raw
        },
        TagDescriptor(ExifInterface.TAG_GPS_ALTITUDE_REF, "GPS 고도 기준", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_DATESTAMP, "GPS 날짜 (UTC)", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_TIMESTAMP, "GPS 시각 (UTC)", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_SPEED, "GPS 이동 속도", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_IMG_DIRECTION, "촬영 방위각", ExifCategory.LOCATION),
        TagDescriptor(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS 측위 방식", ExifCategory.LOCATION),

        // Technical / Image
        TagDescriptor(ExifInterface.TAG_IMAGE_WIDTH, "이미지 너비 (Width)", ExifCategory.TECHNICAL) { _, raw -> "$raw px" },
        TagDescriptor(ExifInterface.TAG_IMAGE_LENGTH, "이미지 높이 (Height)", ExifCategory.TECHNICAL) { _, raw -> "$raw px" },
        TagDescriptor(ExifInterface.TAG_PIXEL_X_DIMENSION, "유효 픽셀 X", ExifCategory.TECHNICAL) { _, raw -> "$raw px" },
        TagDescriptor(ExifInterface.TAG_PIXEL_Y_DIMENSION, "유효 픽셀 Y", ExifCategory.TECHNICAL) { _, raw -> "$raw px" },
        TagDescriptor(ExifInterface.TAG_ORIENTATION, "방향 (Orientation)", ExifCategory.TECHNICAL) { _, raw ->
            decodeOrientation(raw)
        },
        TagDescriptor(ExifInterface.TAG_COLOR_SPACE, "색 공간 (Color Space)", ExifCategory.TECHNICAL) { _, raw ->
            decodeColorSpace(raw)
        },
        TagDescriptor(ExifInterface.TAG_X_RESOLUTION, "수평 해상도 (X Res)", ExifCategory.TECHNICAL),
        TagDescriptor(ExifInterface.TAG_Y_RESOLUTION, "수직 해상도 (Y Res)", ExifCategory.TECHNICAL),
        TagDescriptor(ExifInterface.TAG_RESOLUTION_UNIT, "해상도 단위", ExifCategory.TECHNICAL) { _, raw ->
            when (raw.toIntOrNull()) {
                2 -> "인치 (DPI)"
                3 -> "센티미터 (dpcm)"
                else -> raw
            }
        },
        TagDescriptor(ExifInterface.TAG_COMPRESSION, "압축 방식", ExifCategory.TECHNICAL) { _, raw ->
            decodeCompression(raw)
        },
        TagDescriptor(ExifInterface.TAG_BITS_PER_SAMPLE, "샘플당 비트 수", ExifCategory.TECHNICAL),
        TagDescriptor(ExifInterface.TAG_SAMPLES_PER_PIXEL, "픽셀당 샘플 수", ExifCategory.TECHNICAL),
        TagDescriptor(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, "광도 해석", ExifCategory.TECHNICAL),
        TagDescriptor(ExifInterface.TAG_Y_CB_CR_POSITIONING, "YCbCr 샘플링 위치", ExifCategory.TECHNICAL),
        TagDescriptor(ExifInterface.TAG_SENSING_METHOD, "센서 방식", ExifCategory.TECHNICAL) { _, raw ->
            decodeSensingMethod(raw)
        },
        TagDescriptor(ExifInterface.TAG_CUSTOM_RENDERED, "커스텀 렌더링", ExifCategory.TECHNICAL) { _, raw ->
            decodeCustomRendered(raw)
        },

        // Other
        TagDescriptor(ExifInterface.TAG_IMAGE_DESCRIPTION, "이미지 설명", ExifCategory.OTHER),
        TagDescriptor(ExifInterface.TAG_USER_COMMENT, "사용자 코멘트", ExifCategory.OTHER),
        TagDescriptor(ExifInterface.TAG_IMAGE_UNIQUE_ID, "이미지 고유 ID", ExifCategory.OTHER)
    )

    private fun ExifInterface.cleanAttr(tag: String): String? {
        return getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun formatAperture(exif: ExifInterface): String? {
        val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN)
        val value = if (!fNumber.isNaN() && fNumber > 0) {
            fNumber
        } else {
            val apex = exif.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, Double.NaN)
            if (!apex.isNaN()) 2.0.pow(apex / 2.0) else Double.NaN
        }
        if (value.isNaN() || value <= 0) return null
        val rounded = (value * 10.0).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            "f/${rounded.toInt()}"
        } else {
            String.format(Locale.US, "f/%.1f", rounded)
        }
    }

    private fun formatShutterSpeed(exif: ExifInterface): String? {
        val expTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN)
        val seconds = if (!expTime.isNaN() && expTime > 0) {
            expTime
        } else {
            val tv = exif.getAttributeDouble(ExifInterface.TAG_SHUTTER_SPEED_VALUE, Double.NaN)
            if (!tv.isNaN()) 2.0.pow(-tv) else Double.NaN
        }
        if (seconds.isNaN() || seconds <= 0) return null
        return if (seconds >= 0.5) {
            if (seconds % 1.0 < 0.05) {
                "${seconds.roundToInt()}s"
            } else {
                String.format(Locale.US, "%.1fs", seconds)
            }
        } else {
            val denominator = (1.0 / seconds).roundToInt().coerceAtLeast(1)
            "1/${denominator}s"
        }
    }

    private fun formatIso(exif: ExifInterface): String? {
        val iso = exif.cleanAttr(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?: exif.cleanAttr(ExifInterface.TAG_ISO_SPEED)
            ?: exif.cleanAttr(ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX)
            ?: return null
        return "ISO $iso"
    }

    private fun formatFocalLength(exif: ExifInterface): String? {
        val raw = exif.cleanAttr(ExifInterface.TAG_FOCAL_LENGTH) ?: return null
        val mm = parseRationalOrDouble(raw) ?: return raw
        return if (abs(mm - mm.roundToInt()) < 0.05) {
            "${mm.roundToInt()}mm"
        } else {
            String.format(Locale.US, "%.1fmm", mm)
        }
    }

    private fun formatExposureBias(exif: ExifInterface): String? {
        val bias = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN)
        if (bias.isNaN()) return null
        if (abs(bias) < 0.01) return "0.0 EV"
        val sign = if (bias > 0) "+" else ""
        return String.format(Locale.US, "%s%.1f EV", sign, bias)
    }

    private fun formatExifDateTime(
        dateRaw: String?,
        offsetRaw: String?,
        subsecRaw: String?
    ): String? {
        if (dateRaw == null) return null
        // EXIF format is usually "YYYY:MM:DD HH:MM:SS"
        val parts = dateRaw.split(" ")
        val formattedDate = if (parts.size == 2) {
            val d = parts[0].replace(':', '.')
            val t = parts[1]
            if (subsecRaw != null) "$d $t.$subsecRaw" else "$d $t"
        } else {
            dateRaw
        }
        return if (offsetRaw != null) "$formattedDate ($offsetRaw)" else formattedDate
    }

    private fun parseRationalOrDouble(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.contains('/')) {
            val parts = trimmed.split('/')
            if (parts.size == 2) {
                val num = parts[0].toDoubleOrNull() ?: return null
                val den = parts[1].toDoubleOrNull() ?: return null
                if (den != 0.0) return num / den
            }
        }
        return trimmed.toDoubleOrNull()
    }

    private fun toDms(coord: Double, posDir: String, negDir: String): String {
        val dir = if (coord >= 0) posDir else negDir
        val absVal = abs(coord)
        val deg = absVal.toInt()
        val minFloat = (absVal - deg) * 60.0
        val min = minFloat.toInt()
        val sec = (minFloat - min) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, dir)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun resolveFormatLabel(mimeType: String?, fileName: String?): String {
        val ext = fileName?.substringAfterLast('.', "")?.uppercase(Locale.US)
        return when {
            mimeType == "image/jpeg" -> "JPEG"
            mimeType == "image/png" -> "PNG"
            mimeType == "image/heic" || mimeType == "image/heif" -> "HEIC"
            mimeType == "image/webp" -> "WebP"
            mimeType == "image/x-adobe-dng" || ext == "DNG" -> "RAW (DNG)"
            !ext.isNullOrBlank() && ext.length <= 5 -> ext
            !mimeType.isNullOrBlank() -> mimeType.substringAfter('/').uppercase(Locale.US)
            else -> "IMAGE"
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x.coerceAtLeast(1)
    }

    private fun decodeOrientation(raw: String): String = when (raw.toIntOrNull()) {
        ExifInterface.ORIENTATION_NORMAL -> "정방향 (0°)"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "좌우 반전"
        ExifInterface.ORIENTATION_ROTATE_180 -> "180° 회전"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "상하 반전"
        ExifInterface.ORIENTATION_TRANSPOSE -> "대각선 반전 (Transpose)"
        ExifInterface.ORIENTATION_ROTATE_90 -> "90° 시계방향 회전"
        ExifInterface.ORIENTATION_TRANSVERSE -> "역대각선 반전 (Transverse)"
        ExifInterface.ORIENTATION_ROTATE_270 -> "270° 시계방향 회전"
        else -> "알 수 없음 ($raw)"
    }

    private fun decodeColorSpace(raw: String): String = when (raw.toIntOrNull()) {
        1 -> "sRGB"
        2 -> "Adobe RGB"
        65535 -> "Uncalibrated (Display P3 등)"
        else -> "코드 $raw"
    }

    private fun decodeExposureProgram(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "미정의"
        1 -> "수동 (Manual)"
        2 -> "일반 자동 (Program)"
        3 -> "조리개 우선 (Av / A)"
        4 -> "셔터 우선 (Tv / S)"
        5 -> "크리에이티브 (심도 우선)"
        6 -> "액션 (고속 셔터)"
        7 -> "인물 모드"
        8 -> "풍경 모드"
        else -> "코드 $raw"
    }

    private fun decodeExposureMode(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "자동 노출"
        1 -> "수동 노출"
        2 -> "자동 브라케팅"
        else -> "코드 $raw"
    }

    private fun decodeMeteringMode(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "알 수 없음"
        1 -> "평균 측광"
        2 -> "중앙 중점 측광"
        3 -> "스팟 측광"
        4 -> "멀티 스팟 측광"
        5 -> "멀티 패턴(평가) 측광"
        6 -> "부분 측광"
        255 -> "기타"
        else -> "코드 $raw"
    }

    private fun decodeWhiteBalance(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "자동 (AWB)"
        1 -> "수동 (Manual)"
        else -> "코드 $raw"
    }

    private fun decodeFlash(raw: String): String {
        val code = raw.toIntOrNull() ?: return raw
        val fired = (code and 0x01) != 0
        val mode = (code shr 3) and 0x03
        val base = if (fired) "발광함" else "발광 안 함"
        val modeText = when (mode) {
            1 -> "강제 발광"
            2 -> "강제 비발광"
            3 -> "자동 모드"
            else -> null
        }
        return if (modeText != null) "$base ($modeText)" else base
    }

    private fun decodeLightSource(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "자동 / 알 수 없음"
        1 -> "주광 (Daylight)"
        2 -> "형광등"
        3 -> "텅스텐 (백열등)"
        4 -> "플래시"
        9 -> "맑은 날"
        10 -> "흐린 날"
        11 -> "그늘"
        21 -> "D65 표준 광원"
        else -> "코드 $raw"
    }

    private fun decodeSceneCaptureType(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "표준"
        1 -> "풍경"
        2 -> "인물"
        3 -> "야경"
        else -> "코드 $raw"
    }

    private fun decodeCompression(raw: String): String = when (raw.toIntOrNull()) {
        1 -> "비압축"
        6 -> "JPEG 압축"
        7 -> "JPEG (New)"
        else -> "코드 $raw"
    }

    private fun decodeSensingMethod(raw: String): String = when (raw.toIntOrNull()) {
        1 -> "미정의"
        2 -> "단판 컬러 영역 센서 (1-chip)"
        3 -> "2판 컬러 영역 센서"
        4 -> "3판 컬러 영역 센서"
        else -> "코드 $raw"
    }

    private fun decodeCustomRendered(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "일반 처리"
        1 -> "커스텀 처리 (HDR/보정)"
        else -> "코드 $raw"
    }

    private fun decodeNormalSoftHard(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "표준"
        1 -> "약하게 (Soft)"
        2 -> "강하게 (Hard)"
        else -> raw
    }

    private fun decodeNormalLowHigh(raw: String): String = when (raw.toIntOrNull()) {
        0 -> "표준"
        1 -> "낮음 (Low)"
        2 -> "높음 (High)"
        else -> raw
    }
}
