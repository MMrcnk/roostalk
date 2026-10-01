// Arkusz klienta → Rozszerzenia → Apps Script → wklej → Wdróż → Aplikacja internetowa
// Wykonaj jako: Ja | Kto ma dostęp: Każdy
// Zapisuje do PIERWSZEJ zakładki: A = data, B = numer, C = status
function doGet(e) {
  var p = e.parameter;
  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var cache = CacheService.getScriptCache();
    if (p.id && cache.get('id_' + p.id)) return ContentService.createTextOutput('dup');

    var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];
    if (sheet.getLastRow() === 0) sheet.appendRow(['data', 'numer', 'status']);

    var data = p.ts ? new Date(Number(p.ts)) : new Date();
    sheet.appendRow([data, "'" + (p.caller_id || ''), p.status || '']);

    if (p.id) cache.put('id_' + p.id, '1', 21600);
    return ContentService.createTextOutput('ok');
  } finally {
    lock.releaseLock();
  }
}
