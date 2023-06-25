package com.auth.api.controller;

import com.auth.api.model.PathVariableDto;
import com.auth.api.service.StreamGobbler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/user")
public class VideoController {

    @Autowired
    private S3Client s3Client;

    @Value("${aws.bucket}")
    private String bucket;

    @PostMapping(value = "/video-to-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String convertVideoToAudio(@RequestParam("file") MultipartFile file) {
        try {

            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            // Generate unique filenames using UUID
            String videoFilename = ResourceUtils.getFile("classpath:").getAbsolutePath() + "/video/" + java.util.UUID.randomUUID().toString() + "." + fileExtension;
            String audioFileWithExt = UUID.randomUUID().toString() + ".mp3";
            String audioFilename = ResourceUtils.getFile("classpath:").getAbsolutePath() + "/audio/" + audioFileWithExt;

            // Save the uploaded video file
            File videoFile = new File(videoFilename);
            file.transferTo(videoFile);

            ProcessBuilder processBuilder = null;
            // Convert video to audio using FFmpeg
            if (fileExtension.equalsIgnoreCase("3gp")) {
                processBuilder = new ProcessBuilder("ffmpeg", "-i", videoFile.getAbsolutePath(), "-c:a", "libmp3lame", audioFilename);
            }else{
                processBuilder = new ProcessBuilder("ffmpeg", "-i", videoFile.getAbsolutePath(), audioFilename);
            }
            Process process = processBuilder.start();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            // any output?
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");

            // kick them off
            errorGobbler.start();
            outputGobbler.start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {

                uploadFileInS3(audioFileWithExt, audioFilename);

                System.out.println("Download completed successfully!");
            } else {
                System.out.println("Download failed with exit code: " + exitCode);
            }

            // Delete the temporary video file
            videoFile.delete();
            return "https://uploadedaudio.s3.us-west-2.amazonaws.com/" + audioFilename;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error converting video to audio";
        }
    }

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String saveAudioFile(@RequestParam("file") MultipartFile file) {
        try {

            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            // Generate unique filenames using UUID
            String audioFilename = ResourceUtils.getFile("classpath:").getAbsolutePath() + "/audio/" + file.getOriginalFilename();

            uploadFileInS3(originalFilename, File.createTempFile("originalFilename", null));

            System.out.println("Download uploaded successfully!");

            return "https://uploadedaudio.s3.us-west-2.amazonaws.com/" + audioFilename;
        }
        catch (IOException e) {
            e.printStackTrace();
            return "Error converting video to audio";
        }
    }

    private void uploadFileInS3(String audioFileWithExt, String audioFilename) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(audioFileWithExt)
                .acl("public-read-write")
                .build();

        s3Client.putObject(request, RequestBody.fromFile(new File(audioFilename)));

        S3Waiter waiter = s3Client.waiter();
        HeadObjectRequest requestWait = HeadObjectRequest.builder().bucket(bucket).key(audioFileWithExt).build();

        WaiterResponse<HeadObjectResponse> waiterResponse = waiter.waitUntilObjectExists(requestWait);

        waiterResponse.matched().response().ifPresent(System.out::println);
    }

    private void uploadFileInS3(String audioFileWithExt, File audioFilename) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(audioFileWithExt)
                .acl("public-read-write")
                .build();

        s3Client.putObject(request, RequestBody.fromFile(audioFilename));

        S3Waiter waiter = s3Client.waiter();
        HeadObjectRequest requestWait = HeadObjectRequest.builder().bucket(bucket).key(audioFileWithExt).build();

        WaiterResponse<HeadObjectResponse> waiterResponse = waiter.waitUntilObjectExists(requestWait);

        waiterResponse.matched().response().ifPresent(System.out::println);
    }

    @PostMapping(value = "/youtube/video-to-audio")
    public String convertVideoToAudioForLink(@org.springframework.web.bind.annotation.RequestBody PathVariableDto pathVariableDto) throws FileNotFoundException {
        String fileNameWithExt = UUID.randomUUID().toString() + ".mp3";
        String outputFilePath = ResourceUtils.getFile("classpath:").getAbsolutePath() + "/audio/" + fileNameWithExt;
        String URL = pathVariableDto.getUrl();
        try {
            // Build the command
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "yt-dlp",
                    "--extract-audio",
                    "--audio-format", "mp3",
                    "--audio-quality", "0",
                    "--output", outputFilePath,
                    URL
            );

            // Redirect the error stream to the standard output
            processBuilder.redirectErrorStream(true);

            // Start the process
            Process process = processBuilder.start();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            // any output?
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");

            // kick them off
            errorGobbler.start();
            outputGobbler.start();

            // Read the output of the process
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Download completed successfully!");
                uploadFileInS3(fileNameWithExt, outputFilePath);
            } else {
                System.out.println("Download failed with exit code: " + exitCode);
            }
            return "https://uploadedaudio.s3.us-west-2.amazonaws.com/" + fileNameWithExt;
            // return outputFilePath;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error converting video to audio";
        }
    }
}
