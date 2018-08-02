if(typeof require !== "undefined"){
	var dialog = remote.dialog;
	var packageFile = remote.require('./package.json');
	var mainApp = remote.require('./app');
	var path = remote.require('path');
}
const version = (typeof packageFile === 'undefined') ? $("#appVersionFallback").text() : packageFile.version;

(function () {
	//open links externally by default
	$(document).on('click', 'a[href^="http"]', function (event) {
		event.preventDefault();
		shell.openExternal(this.href);
	});

	$(document).on('change', 'input:checkbox.selectAll', function(){
		$('input:checkbox.trackCheckbox').prop('checked', $(this).prop('checked'));
	});

	// Ready state of the page
	document.onreadystatechange = function () {
		if (document.readyState == "complete") {
			$('#application_version_about').text(version);
			$('#application_version_logo').text(version.replace(/\.[^/.]+$/, ""));

			$('#modal_settings_input_downloadTracksLocation').on('click', function () {
				if(typeof require !== "undefined"){
					$(this).val(dialog.showOpenDialog({
						properties: ['openDirectory']
					}));
				}
			});
		}
	};
})(jQuery);
