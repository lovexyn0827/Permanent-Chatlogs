const toolTip = document.createElement("div");
	toolTip.setAttribute("class", "tooltip");
	toolTip.style.display = "none";

	const spanIds = collectSpanIds();

	function collectSpanIds() {
		const set = new Set();
		for (var id in hoverEvents) {
			set.add(id);
		}
		
		for (var id in clickEvents) {
			set.add(id);
		}
		
		return set;
	}

	const markers = document.getElementsByClassName("msg_marker");
	for (var i = 0; i < markers.length; i++) {
		var m = markers.item(i);
		if (!m.hasAttribute("data-sent-time")) {
			continue;
		}
		
		m.addEventListener("mousemove", (event) => {
			var timestamp = parseInt(event.currentTarget.getAttribute("data-sent-time"), 10);
			var time = new Date(timestamp).toLocaleString() + "." + timestamp % 1000;
			showToolTip(event.pageX, event.pageY, event.currentTarget, 
						{action: "show_text", value: time}, false, false);
		});
		m.addEventListener("mouseout", (event) => {
			toolTip.style.display = "none";
		});
	}
	
	for (var sId of spanIds) {
		if (!sId.startsWith("t_")) {
			continue;
		}
		
		var span = document.getElementById(sId);
		const updateToolTipFunc = (event) => {
			var id = event.currentTarget.getAttribute("id");
			var e = (event.altKey ? clickEvents : hoverEvents)[id];
			if (e == undefined) {
				return;
			}
			
			showToolTip(event.pageX, event.pageY, event.currentTarget, e, event.altKey, false);
		};
		span.addEventListener("mousemove", updateToolTipFunc);
		span.addEventListener("mouseleave", (event) => {
			toolTip.style.display = "none";
		});
		span.addEventListener("mousedown", (event) => {
			var id = event.currentTarget.getAttribute("id");
			trySetClipboard((event.altKey ? clickEvents : hoverEvents)[id].value);
		});
	}

	function trySetClipboard(value) {
		if (!("clipboard" in navigator)) {
			alert("Clipboard is not supported by the browser!");
			return;
		}
		
		// Permission
		
		navigator.clipboard.writeText(value);
	}

	function showToolTip(x, y, span, e, isClickEvent, retainPos) {
		//if (!span.hasAttribute("data-has-tooltip")) {
			
			//span.setAttribute("data-has-tooltip", true);
		//}
		toolTip.style.display = "";
		var text = e.value.replaceAll("\n", "</br>");
		toolTip.innerHTML = isClickEvent ? text : text;
		span.appendChild(toolTip);
		if (!retainPos) {
			toolTip.style.left = x + "px";
			toolTip.style.top = y + "px";
		}
	}