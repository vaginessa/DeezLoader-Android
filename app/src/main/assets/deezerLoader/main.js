// Load settings before everything
let appConfig;
const fs = require("fs-extra");
const path = require('path');
const os = require('os');
loadSettings();

const theApp = require('./app');

const url = require('url');

let mainWindow;

function loadSettings(){
	userdata = homedata = "/storage/emulated/0/DeezLoader";

	if(!fs.existsSync(userdata+"config.json")){
		fs.outputFileSync(userdata+"config.json",fs.readFileSync(__dirname+path.sep+"default.json",'utf8'));
	}

	appConfig = require(userdata+path.sep+"config.json");

	if( typeof appConfig.userDefined.numplaylistbyalbum != "boolean" ||
			typeof appConfig.userDefined.syncedlyrics != "boolean" ||
		 	typeof appConfig.userDefined.padtrck != "boolean" ||
	 		typeof appConfig.userDefined.albumNameTemplate != "string"
		){
		fs.outputFileSync(userdata+"config.json",fs.readFileSync(__dirname+path.sep+"default.json",'utf8'));
		appConfig = require(userdata+path.sep+"config.json");
	}
}