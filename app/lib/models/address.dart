class Address {
  final String? id;
  final String name;
  final String mobile;
  final String house;
  final String street;
  final String area;
  final String city;
  final String state;
  final String pincode;
  final String country;
  final double? latitude;
  final double? longitude;
  final bool isDefault;

  const Address({
    this.id,
    required this.name,
    required this.mobile,
    required this.house,
    required this.street,
    required this.area,
    required this.city,
    required this.state,
    required this.pincode,
    this.country = 'India',
    this.latitude,
    this.longitude,
    this.isDefault = false,
  });

  factory Address.fromJson(Map<String, dynamic> json) => Address(
        id: json['id']?.toString(),
        name: json['name']?.toString() ?? json['fullName']?.toString() ?? '',
        mobile: json['mobile']?.toString() ??
            json['mobileNumber']?.toString() ??
            '',
        house: json['house']?.toString() ?? json['houseFlat']?.toString() ?? '',
        street: json['street']?.toString() ?? '',
        area: json['area']?.toString() ?? '',
        city: json['city']?.toString() ?? 'Hyderabad',
        state: json['state']?.toString() ?? 'Telangana',
        pincode: json['pincode']?.toString() ?? '',
        country: json['country']?.toString() ?? 'India',
        latitude: (json['latitude'] as num?)?.toDouble(),
        longitude: (json['longitude'] as num?)?.toDouble(),
        isDefault: json['isDefault'] == true,
      );

  String get oneLine => '$house, $street, $area, $city - $pincode';
  String get fullAddress => oneLine;

  Map<String, dynamic> toJson() => {
        if (id != null) 'id': id,
        'label': 'Home',
        'name': name,
        'recipientName': name,
        'fullName': name,
        'mobile': mobile,
        'phone': mobile,
        'house': house,
        'line1': house,
        'street': street,
        'line2': street,
        'area': area,
        'landmark': area,
        'addressLine': [house, street, area]
            .where((part) => part.trim().isNotEmpty)
            .join(', '),
        'city': city,
        'state': state,
        'pincode': pincode,
        'postalCode': pincode,
        'country': country,
        'latitude': latitude,
        'longitude': longitude,
        'isDefault': isDefault,
        'defaultAddress': isDefault,
      };

  Address copyWith({
    String? id,
    String? name,
    String? mobile,
    String? house,
    String? street,
    String? area,
    String? city,
    String? state,
    String? pincode,
    String? country,
    double? latitude,
    double? longitude,
    bool? isDefault,
  }) =>
      Address(
        id: id ?? this.id,
        name: name ?? this.name,
        mobile: mobile ?? this.mobile,
        house: house ?? this.house,
        street: street ?? this.street,
        area: area ?? this.area,
        city: city ?? this.city,
        state: state ?? this.state,
        pincode: pincode ?? this.pincode,
        country: country ?? this.country,
        latitude: latitude ?? this.latitude,
        longitude: longitude ?? this.longitude,
        isDefault: isDefault ?? this.isDefault,
      );
}
