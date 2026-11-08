# CometEats Menu — System Specification: Introduction & Scope
## 1. System Introduction
  CometEats Menu is an automated, web-based dining menu aggregator designed for University of Texas at Dallas (UTD) students.
  The system automatically scrapes and ingests daily menu data, meal periods, serving stations, and official dietary tags from UTD’s official Dine on Campus portal. It presents students with a fast, mobile-friendly interface to view daily dining options, filter dishes by dietary preferences, and submit verified peer feedback without relying on university Single Sign-On (SSO) infrastructure.   
## 2. System Scope
### Functions:
* Automated Data Ingestion: Scheduled scraper imports daily menus (location, date, meal period, station, dish, dietary tags) from Dine on Campus at least once every 6 hours.
* Fallback Display: Maintains cached menu state with a "last updated" timestamp when Dine on Campus is unreachable.
* Public Menu Browsing: Anonymous access to browse dining hall menus and filter dishes by imported dietary tags.
* Student Authentication: Registration and sign-in restricted to UTD email accounts (@utdallas.edu) verified via a third-party email verification service.
* Ratings & Reviews: Signed-in students can submit a 1–5 star rating and a written review (up to 280 characters) per dish per day.
* Community Moderation: Signed-in students can report reviews; any review accumulating 3 distinct reports is automatically hidden from public view.   
