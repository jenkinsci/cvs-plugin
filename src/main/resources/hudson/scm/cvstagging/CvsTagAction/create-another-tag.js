document.addEventListener('DOMContentLoaded', () => {

    document.getElementById('create-another-tag-button')
        .addEventListener('click', () => {
            document.getElementById('tagForm').show();
            document.getElementById('tagButton').style.display = 'none';
        });
});

