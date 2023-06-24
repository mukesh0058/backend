package com.auth.api.controller;

import com.auth.api.service.StreamGobbler;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/user")
public class VideoController {

    @PostMapping(value = "/video-to-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String convertVideoToAudio(@RequestParam("file") MultipartFile file) {
        try {

            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
            // Generate unique filenames using UUID
            String videoFilename = ResourceUtils.getFile("classpath:").getAbsolutePath() + "\\audio\\" + java.util.UUID.randomUUID().toString() + "." + fileExtension;
            String audioFilename = ResourceUtils.getFile("classpath:").getAbsolutePath() + "\\video\\" + java.util.UUID.randomUUID().toString() + ".mp3";

            // Save the uploaded video file
            File videoFile = new File(videoFilename);
            file.transferTo(videoFile);

            // Convert video to audio using FFmpeg
            ProcessBuilder processBuilder = new ProcessBuilder("C:\\ffmpeg\\bin\\ffmpeg", "-i", videoFile.getAbsolutePath(), audioFilename);
            Process process = processBuilder.start();
            // process.waitFor();

            // Delete the temporary video file
            //   videoFile.delete();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
            // any output?
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");

            // kick them off
            errorGobbler.start();
            outputGobbler.start();

            // any error???
            int exitVal = process.waitFor();

            return audioFilename;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error converting video to audio";
        }
    }
}
