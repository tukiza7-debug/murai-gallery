import 'package:aves/model/entry/entry.dart';
import 'package:aves/model/entry/extensions/favourites.dart';
import 'package:aves/utils/time_utils.dart';
import 'package:collection/collection.dart';

class AvesEntrySort {
  // compare by:
  // 1) title ascending
  // 2) extension ascending
  static int compareByName(AvesEntry a, AvesEntry b) {
    final c = compareAsciiUpperCaseNatural(a.bestTitle ?? '', b.bestTitle ?? '');
    return c != 0 ? c : compareAsciiUpperCase(a.extension ?? '', b.extension ?? '');
  }

  // compare by:
  // 1) date descending
  // 2) name descending
  static int compareByDate(AvesEntry a, AvesEntry b) {
    var c = (b.bestDate ?? epoch).compareTo(a.bestDate ?? epoch);
    if (c != 0) return c;
    return compareByName(b, a);
  }

  // compare by:
  // 1) favourites first
  // 2) rating descending
  // 3) date descending
  static int compareByRating(AvesEntry a, AvesEntry b) {
    final c = (b.isFavourite ? 1 : 0).compareTo(a.isFavourite ? 1 : 0);
    if (c != 0) return c;
    final r = b.rating.compareTo(a.rating);
    return r != 0 ? r : compareByDate(a, b);
  }

  // murai: compare by:
  // 1) date added descending (missing values last)
  // 2) date descending
  static int compareByDateAdded(AvesEntry a, AvesEntry b) {
    final c = (b.dateAddedSecs ?? 0).compareTo(a.dateAddedSecs ?? 0);
    return c != 0 ? c : compareByDate(a, b);
  }

  // murai: compare by:
  // 1) extension ascending, missing last
  // 2) name ascending
  static int compareByType(AvesEntry a, AvesEntry b) {
    const lastSentinel = '\u{10FFFF}';
    final ae = a.extension ?? lastSentinel;
    final be = b.extension ?? lastSentinel;
    final c = compareAsciiUpperCase(ae, be);
    if (c != 0) return c == 0 ? compareByName(a, b) : c;
    // same sentinel on both sides: order by name, and put both-missing group last implicitly
    if (a.extension == null && b.extension == null) return compareByName(a, b);
    return compareByName(a, b);
  }

  // murai: compare by:
  // 1) megapixels descending (missing last)
  // 2) size descending
  static int compareByResolution(AvesEntry a, AvesEntry b) {
    final ap = a.width <= 0 || a.height <= 0 ? -1 : a.width * a.height;
    final bp = b.width <= 0 || b.height <= 0 ? -1 : b.width * b.height;
    final c = bp.compareTo(ap);
    return c != 0 ? c : compareBySize(a, b);
  }

  // murai: compare by:
  // 1) country ascending, missing last
  // 2) place ascending, missing last
  // 3) name ascending
  static int compareByLocation(AvesEntry a, AvesEntry b) {
    const lastSentinel = '\u{10FFFF}';
    final ac = a.addressDetails?.countryName;
    final bc = b.addressDetails?.countryName;
    if (ac == null && bc != null) return 1;
    if (ac != null && bc == null) return -1;
    var c = compareAsciiUpperCase(ac ?? lastSentinel, bc ?? lastSentinel);
    if (c != 0) return c;
    final ap = a.addressDetails?.place;
    final bp = b.addressDetails?.place;
    if (ap == null && bp != null) return 1;
    if (ap != null && bp == null) return -1;
    c = compareAsciiUpperCase(ap ?? lastSentinel, bp ?? lastSentinel);
    return c != 0 ? c : compareByName(a, b);
  }

  // compare by:
  // 1) size descending
  // 2) date descending
  static int compareBySize(AvesEntry a, AvesEntry b) {
    final c = (b.sizeBytes ?? 0).compareTo(a.sizeBytes ?? 0);
    return c != 0 ? c : compareByDate(a, b);
  }

  // compare by:
  // 1) duration descending
  // 2) date descending
  static int compareByDuration(AvesEntry a, AvesEntry b) {
    final c = (b.durationMillis ?? 0).compareTo(a.durationMillis ?? 0);
    return c != 0 ? c : compareByDate(a, b);
  }

  // compare by:
  // 1) path ascending
  static int compareByPath(AvesEntry a, AvesEntry b) {
    return compareAsciiUpperCase(a.path ?? '', b.path ?? '');
  }
}
