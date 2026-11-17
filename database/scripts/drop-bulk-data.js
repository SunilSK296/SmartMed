// mongosh script: removes the records created by bulk-data.js
const meds = db.medicines.find({ code: /^BULK-/ }, { _id: 1 }).toArray().map((m) => m._id.toHexString());
printjson(db.stock_batches.deleteMany({ medicineId: { $in: meds } }));
printjson(db.medicines.deleteMany({ code: /^BULK-/ }));
