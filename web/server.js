const express = require('express');
const fs = require('fs');
const path = require('path');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server);
// Allow overriding plugin data directory via environment variable
const pluginData = process.env.PLUGIN_DATA ||
  path.join(__dirname, '../plugins/xFlyPlot');
const dataFile = path.join(pluginData, 'stats.json');
const infoFile = path.join(pluginData, 'server.json');

app.use(express.static(path.join(__dirname, 'public')));

function readJson(file, empty){
  try{ return JSON.parse(fs.readFileSync(file, 'utf8')); }catch(e){ return empty; }
}

io.on('connection', socket => {
  socket.emit('update', { stats: readJson(dataFile, []), server: readJson(infoFile, {}) });
});

setInterval(() => {
  io.emit('update', { stats: readJson(dataFile, []), server: readJson(infoFile, {}) });
}, 5000);

server.listen(3000, () => console.log('Dashboard running on http://localhost:3000'));
