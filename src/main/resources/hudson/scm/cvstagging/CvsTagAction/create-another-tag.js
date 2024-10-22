document.addEventListener('DOMContentLoaded', () => {
    document.querySelector('#tagButton input').addEventListener('click', () => {
        document.getElementById('tagForm').style.display = 'block';
        document.getElementById('tagButton').style.display = 'none';
    });
});
