document.addEventListener('DOMContentLoaded', () => {

    document.getElementById('create-another-tag-button')
        .addEventListener('click', () => {
            document.getElementById('tagForm').style.display = 'block';
            document.getElementById('tagButton').style.display = 'none';
        });
});