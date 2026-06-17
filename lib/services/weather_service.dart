import 'dart:convert';
import 'package:http/http.dart' as http;

class WeatherService {
  static const Map<String, Map<String, double>> _cityCoordinates = {
    'chennai': {'lat': 13.0827, 'lon': 80.2707},
    'mumbai': {'lat': 19.0760, 'lon': 72.8777},
    'delhi': {'lat': 28.6139, 'lon': 77.2090},
    'bangalore': {'lat': 12.9716, 'lon': 77.5946},
    'hyderabad': {'lat': 17.3850, 'lon': 78.4867},
    'kolkata': {'lat': 22.5726, 'lon': 88.3639},
    'pune': {'lat': 18.5204, 'lon': 73.8567},
    'ahmedabad': {'lat': 23.0225, 'lon': 72.5714},
    'coimbatore': {'lat': 11.0168, 'lon': 76.9558},
    'madurai': {'lat': 9.9252, 'lon': 78.1198},
  };

  Future<Map<String, dynamic>> getWeatherData(String cityName) async {
    try {
      final city = cityName.toLowerCase().trim();
      final coords = _cityCoordinates[city];

      double lat;
      double lon;

      if (coords != null) {
        lat = coords['lat']!;
        lon = coords['lon']!;
      } else {
        lat = 13.0827;
        lon = 80.2707;
      }

      final url = Uri.parse(
        'https://api.open-meteo.com/v1/forecast'
        '?latitude=$lat'
        '&longitude=$lon'
        '&current=temperature_2m,relative_humidity_2m'
        '&timezone=Asia/Kolkata',
      );

      final response = await http.get(url).timeout(
        const Duration(seconds: 10),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final current = data['current'];
        return {
          'ambientTemperatureC': (current['temperature_2m'] as num).toDouble(),
          'humidity': (current['relative_humidity_2m'] as num).toDouble(),
          'cityName': cityName,
        };
      } else {
        return _defaultWeather(cityName);
      }
    } catch (e) {
      return _defaultWeather(cityName);
    }
  }

  Map<String, dynamic> _defaultWeather(String cityName) {
    return {
      'ambientTemperatureC': 0.0,
      'humidity': 0.0,
      'cityName': cityName,
    };
  }
}