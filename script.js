(function() {
	"use strict";
	var socket;
	var name;
	var color = new ColorCoordinator();
	
	function $(ele) {
		return document.getElementById(ele);
	}
	
	window.addEventListener("load", function() {
		var URL = "ws://" + location.host + ":3616/";
		socket = new WebSocket(URL);
		var name = $("nameset");
		name.addEventListener("keydown", nameEnter);
		var send = $("msg");
		send.addEventListener("keydown", msgEnter);
		socket.onmessage = msg;
        // for some reason some styles aren't working so...
        window.addEventListener("resize", resize);
        resize();
	});
	
	function msg(e) {
		var data = e.data;
		var part = data.split("\n");
		addMessage(part[0], part[1]);
	}
	
	function nameEnter(e) {
		if (e.keyCode == 13) {
			var ns = $("nameset");
			name = ns.value;
			socket.send(ns.value);
			ns.parentNode.removeChild(this);
			$("overlay").hidden = true;
			$("msg").focus();
		}
	}
	
	function msgEnter(e) {
		if (e.keyCode == 13) {
			var msg = $("msg");
			addMessage(name, msg.value);
			socket.send(msg.value);
			msg.value = "";
		}
	}
	
	function addMessage(name, str) {
		var txt = document.createElement("div");
		var mes = document.createElement("span");
		mes.innerHTML = str;
		mes.classList.add("chat");
		var nm = document.createElement("span");
		nm.innerHTML = name + ": ";
		nm.classList.add("name");
		nm.style.color = color.get(name);
		txt.appendChild(nm);
		txt.appendChild(mes);
        var cl = $("chatlog");
		cl.insertBefore(txt, cl.firstChild);
        if (str.toLowerCase().includes(name)) {
            notify(str);
        }
	}
	
	function ColorCoordinator() {
		this.names = new Array();
		this.color = new Array();
		this.get = function (n) {
			var index = -1;
			for(var i = 0; i < this.names.length; i++) {
				if(n == this.names[i]) {
					index = i;
				}
			}
			if (index == -1) {
				this.names.push(n);
				this.color.push("hsl( " + Math.round(Math.random() * 360) + ", " + Math.round(Math.random() * 100) + "%, " +
			  		Math.round(Math.random() * 100) + "%)");
				index = this.names.length - 1;
			}
			return this.color[index];
		};			  
	}

    function notify(message) {
        if ("Notification" in window) {
            if (Notification.permission === "granted") {
                var notification = new Notification(message);
            } else {
                Notification.requestPermission(function(permission) {
                    if (permission === "granted") {
                        var notification = new Notification(message);
                    }
                });
            }
        }
    }

    function resize() {
        var cb = $("chatlog");
        var ms = $("msg");
        cb.style.height = "calc(" + (window.innerHeight - parseInt(window.getComputedStyle(ms).height)) + "px" + " - 3em)";
    }
})();
