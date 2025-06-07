const express = require('express');
const fs = require('fs');
const path = require('path');

const app = express();
const dataFile = path.join(__dirname, '../build/resources/main/stats.json');
const infoFile = path.join(__dirname, '../build/resources/main/server.json');

app.use(express.static(path.join(__dirname, 'public')));

app.get('/stats', (req, res) => {
  fs.readFile(dataFile, 'utf8', (err, data) => {
    if (err) return res.json([]);
    try { res.json(JSON.parse(data)); } catch (e) { res.json([]); }
  });
});

app.get('/server', (req, res) => {
  fs.readFile(infoFile, 'utf8', (err, data) => {
    if (err) return res.json({});
    try { res.json(JSON.parse(data)); } catch (e) { res.json({}); }
  });
});

app.listen(3000, () => console.log('Dashboard running on http://localhost:3000'));
