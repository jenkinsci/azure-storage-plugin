document.addEventListener('DOMContentLoaded', () => {
  const button = document.getElementById("azure-storage-add-credentials")
  const credentialsUri = button.dataset.credentialsUri
  button.addEventListener('click', (e) => {
    e.preventDefault();
    window.credentials.add(credentialsUri)
  })
})
