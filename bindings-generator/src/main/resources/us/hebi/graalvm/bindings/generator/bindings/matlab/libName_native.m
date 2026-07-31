function varargout = %%LIB_NAME%%_native(funcName, varargin)
% %%LIB_NAME%%_native MEX setup call that gets shadowed once the MEX is available

%% Getting here means that the mex file is not on the path or not compatible
mexName = ['%%LIB_NAME%%_native.' mexext];
thisDir = fileparts(mfilename('fullpath'));
localMex = fullfile(thisDir, mexName);

%% We first try to get it from the downloaded distribution
info = locate_binary();
shippedMex = fullfile(info.binDir, mexName);
if exist(shippedMex, 'file')
    try

        % Copy and load mex file
        fprintf('Installing pre-compiled MEX from: %s\n', mexName);
        copyfile(shippedMex, localMex);
        clear('%%LIB_NAME%%_native');
        rehash;

        % Try using it
        if exist('%%LIB_NAME%%_native', 'file') == 3 % MEX_FILE
            [varargout{1:nargout}] = %%LIB_NAME%%_native(funcName, varargin{:});
            return;
        end

    catch
        % Not compatible -> proceed with building from scratch
        if exist(localMex, 'file')
            fprintf('Removing incompatible MEX file: %s\n', localMex);
            delete(localMex);
        end
    end
end

%% Compile for the target platform
try
    build_native();
catch ME
    error('%%LIB_NAME%%:BuildError', 'Could not build MEX: %s', ME.message);
end

% Verify that MATLAB finds the new MEX function
if exist('%%LIB_NAME%%_native', 'file') ~= 3 % MEX_FILE
    error('Newly compiled MEX file not found');
end

% Delegate to the newly compiled method
try
    [varargout{1:nargout}] = %%LIB_NAME%%_native(funcName, varargin{:});
catch ME
    error('MEX compiled but failed to execute: %s', ME.message);
end

end