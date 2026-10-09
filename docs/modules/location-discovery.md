# Location and Business Discovery

## Registration
- New customer and business accounts must provide a location label or address.
- Browser GPS permission can supply latitude and longitude during registration. GPS requires browser permission and a secure context (HTTPS, or localhost during development).
- Manual location selection requires a country, region, and city/town. Suggestions are loaded as the user types; the location label and coordinates are generated from the selected place.
- Reverse lookup sends GPS coordinates to OpenStreetMap to identify a city/town.
- The application stores coordinates supplied by the user. It does not run background GPS tracking or call a geocoding provider.

## Customer discovery
- Customers can edit their saved location from the Businesses tab.
- Manual location entry requires country, state/region, and city/town selections. Choosing a city fills the read-only location label and coordinates; users cannot type a custom address or coordinates into the form.
- Nearby results include active businesses with coordinates, sorted by straight-line distance and limited to the selected radius (5, 25, 50, or 100 km in the UI).
- The discovery response contains only business name, category, address, coordinates, and distance. The existing tenant-scoped detail endpoint remains unchanged.

## Business offers
- Business users can publish an offer with a title, discount text, details, and start/end dates, and deactivate posted offers.
- Customers see active offers whose validity window includes the current time and whose active, mapped business is within the selected search radius. Offers are sorted by distance.
- A business must have a mapped location before it can publish offers for nearby discovery.

## Deployment
- Apply `database/migrations/2026-09-29-location-discovery.sql` and `database/migrations/2026-09-29-business-offers.sql` before deployment. Legacy profiles are not assigned inferred coordinates.