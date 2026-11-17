// mongosh script: initiates a single-node replica set (needed for multi-document transactions).
// Usage:  mongosh --port 27017 --file database/scripts/init-replica-set.js
try {
  const s = rs.status();
  print("Replica set already initiated: " + s.set);
} catch (e) {
  printjson(rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "localhost:27017" }] }));
}
