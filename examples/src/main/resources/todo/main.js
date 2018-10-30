// This is synchronous for simplicity.
function client(method, endpoint, body) {
    var api = "http://" + window.location.host + endpoint;
    var xmlHttp = new XMLHttpRequest();
    xmlHttp.open(method, api, false);

    if (body != null) {
      xmlHttp.setRequestHeader("Content-Type", "application/json;charset=UTF-8");
    }

    xmlHttp.send(body);
    return JSON.parse(xmlHttp.responseText);
}

function getTodos() {
    var todos = document.getElementById("todos");
    todos.innerHTML = "";

    var response = client("GET", "/todos", null);

    for (var i in response)
    {
      var li = document.createElement("li");
      var cb = document.createElement("input");
      cb.setAttribute("type", "checkbox");

      if (response[i].completed) {
        cb.setAttribute("checked", "");
      }

      cb.setAttribute("todo-id", response[i].id);
      cb.onclick = toggleTodo;

      li.appendChild(cb);
      li.appendChild(document.createTextNode(response[i].title));
      todos.appendChild(li);
    }
}

function postTodo() {
    var input = document.getElementById("todoInput");
    if (input && input.value != "") {
        client("POST", "/todos", JSON.stringify({ "title": input.value }));
        input.value = "";
        getTodos();
    }
}

function toggleTodo() {
  client("PATCH", "/todos/" + this.getAttribute("todo-id"), JSON.stringify({ "completed": this.checked }));
}

function init() {
    if (document.getElementById("addButton")) {
        document.getElementById("addButton").onclick = postTodo;
        getTodos();
    } else {
        setTimeout(init, 300);
    }
}

init();
