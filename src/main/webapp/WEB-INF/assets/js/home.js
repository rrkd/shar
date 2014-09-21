$(function(){
    // Variable to store your files
    var files;

// Add events
    $('input[type=file]').on('change', function(event){
        generateClientFile(event);

    });

// Grab the files and set them to our variable
    function generateClientFile(event)
    {
        files = event.target.files;
        var file = files[0];
        var reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onload = function(){
            var fileContent = reader.result.match(/,(.*)$/)[1];
            var fileClient = {
                resource_id:"",
                title:file.name,
                description:"test",
                mimeType:file.type,
                content:fileContent,
                parents:[]
            };

            $.ajax({
                type:"POST",
                url:"/svc",
                data:JSON.stringify(fileClient),
                contentType:'application/json',
                success:function (data) {

                }
            });
        }

    }
});