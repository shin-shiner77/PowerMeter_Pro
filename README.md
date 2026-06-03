# PowerMeter Pro v1.0

PowerMeter Pro is a precision power monitoring application for Android devices. It provides real-time analytics for battery consumption and external USB device power draw.

## Key Features

- **Real-time Monitoring**: High-speed sampling (50ms) of battery current (mA) and voltage (V).
- **Power Calculation**: Automatic conversion to milliwatts (mW) for total power consumption.
- **Differential Measurement (USB Lab)**: 
  - **Zero Calibration**: Captures the system's baseline power draw.
  - **USB Tracking**: Subtracts the baseline to measure the exact current consumed by external USB/OTG devices.
- **Advanced Filtering**: Removes measurement peaks and outliers (top/bottom 20%) to provide a stable average.
- **Dynamic Hardware Breakdown**: Estimates power usage for major components:
  - **Display (OLED)**: Adjusted based on real-time screen brightness.
  - **AP (CPU/GPU)**: Estimated based on battery temperature and thermal load.
  - **Radios (WiFi/LTE)**: Based on connectivity status.
  - **System Core**: Base system operations.
- **Visual Analytics**: Real-time line graph for monitoring trends.
- **Detailed Reports**: Comprehensive summary of average draw, system baseline, and hardware distribution.

## Precision Mode (Optional)
To enable higher precision and internal system stats, it is recommended to grant the following permission via ADB:
```bash
adb shell pm grant com.example.complementary_filter android.permission.BATTERY_STATS
```

## Developer
- **Version**: 1.0
- **Platform**: Android (Kotlin, Jetpack Compose)
- **GitHub**: [https://github.com/shin-shiner77/PowerMeter_Pro](https://github.com/shin-shiner77/PowerMeter_Pro)
