document.addEventListener("DOMContentLoaded", function () {
  document.querySelector("#tagButton input").addEventListener("click", function () {
    document.getElementById("tagForm").style.display = "block";
    document.getElementById("tagButton").style.display = "none";
  });
});
