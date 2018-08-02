const fs = require("fs-extra");
const path = require('path');
const os = require('os');

homedata = "/storage/emulated/0";
userdata = homedata + "/Deezloader/";

const logsLocation = userdata + "/deezloader.log";

function logs(level, message, callback){
	var str = "["+level+"]"+message;
	console.log(str);
	fs.appendFileSync(logsLocation, str+"\n");
	return;
}

module.exports.logs = logs;
