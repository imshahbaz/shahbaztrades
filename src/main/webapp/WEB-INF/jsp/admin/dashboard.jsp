<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="../common/header.jsp" />

<main class="container flex-grow-1">
    <div class="py-5">
        <div class="row mb-4">
            <div class="col-12">
                <h2 class="text-primary mb-4">
                    <i class="fas fa-tachometer-alt me-2"></i>Admin Dashboard
                </h2>
            </div>
        </div>

        <div class="row">
            <!-- Margin Data Management -->
            <div class="col-lg-6 mb-4">
                <div class="card h-100 shadow">
                    <div class="card-header bg-primary text-white">
                        <h5 class="mb-0">
                            <i class="fas fa-upload me-2"></i>Margin Data Management
                        </h5>
                    </div>
                    <div class="card-body">
                        <p class="text-muted">Upload CSV files to load margin data into the system.</p>

                        <form id="uploadForm" enctype="multipart/form-data">
                            <div class="mb-3">
                                <label for="csvFile" class="form-label">
                                    <i class="fas fa-file-csv me-1"></i>CSV File
                                </label>
                                <input type="file" class="form-control" id="csvFile" name="file" accept=".csv" required>
                                <div class="form-text">Select a CSV file containing margin data</div>
                            </div>

                            <div class="d-grid">
                                <button type="submit" class="btn btn-primary" id="uploadBtn">
                                    <i class="fas fa-upload me-2"></i>Upload and Load Data
                                </button>
                            </div>
                        </form>

                        <div id="result" class="mt-3" style="display: none;">
                            <div id="successAlert" class="alert alert-success" style="display: none;">
                                <i class="fas fa-check-circle me-2"></i>
                                <span id="successMessage"></span>
                            </div>
                            <div id="errorAlert" class="alert alert-danger" style="display: none;">
                                <i class="fas fa-exclamation-triangle me-2"></i>
                                <span id="errorMessage"></span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script>
document.getElementById('uploadForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const formData = new FormData();
    const fileInput = document.getElementById('csvFile');
    const uploadBtn = document.getElementById('uploadBtn');
    const resultDiv = document.getElementById('result');
    const successAlert = document.getElementById('successAlert');
    const errorAlert = document.getElementById('errorAlert');

    if (!fileInput.files[0]) {
        alert('Please select a CSV file');
        return;
    }

    formData.append('file', fileInput.files[0]);

    // Disable button and show loading
    uploadBtn.disabled = true;
    uploadBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Uploading...';

    // Hide previous results
    resultDiv.style.display = 'none';
    successAlert.style.display = 'none';
    errorAlert.style.display = 'none';

    fetch('${pageContext.request.contextPath}/api/margin/load-from-csv', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (response.ok) {
            document.getElementById('successMessage').textContent = 'CSV data loaded successfully!';
            successAlert.style.display = 'block';
        } else {
            throw new Error('Failed to load CSV data');
        }
    })
    .catch(error => {
        document.getElementById('errorMessage').textContent = error.message;
        errorAlert.style.display = 'block';
    })
    .finally(() => {
        // Re-enable button
        uploadBtn.disabled = false;
        uploadBtn.innerHTML = '<i class="fas fa-upload me-2"></i>Upload and Load Data';
        resultDiv.style.display = 'block';
    });
});
</script>

<jsp:include page="../common/footer.jsp" />
