function download_binary(downloadDir, dlName)
%DOWNLOAD_BINARY downloads a release archive and extracts it into a directory

url = ['https://example.com/download/bindings_tests/snapshot/42/' dlName];
if ~exist(downloadDir, 'dir'), mkdir(downloadDir); end

fprintf('Downloading %s\n', url);
zipFilePath = fullfile(downloadDir, dlName);
websave(zipFilePath, url);

fprintf('Extracting to %s\n', downloadDir);
unzip(zipFilePath, downloadDir);
delete(zipFilePath);

end
