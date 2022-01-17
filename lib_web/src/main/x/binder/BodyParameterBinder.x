import ecstasy.reflect.Parameter;

import codec.MediaTypeCodec;
import codec.MediaTypeCodecRegistry;

import web.Body;

/**
 * A ParameterBinder that binds a http request request body to a parameter.
 */
class BodyParameterBinder(MediaTypeCodecRegistry registry)
        implements ParameterBinder<HttpRequest>
    {
    @Override
    Int priority.get()
        {
        return DEFAULT_PRIORITY + 1;
        }

    @Override
    Boolean canBind(Parameter parameter)
        {
        if (parameter.is(Body))
            {
            return True;
            }

        if (String name := parameter.hasName())
            {
            return name == "body";
            }

        return False;
        }

    @Override
    <ParamType> BindingResult<ParamType> bind(Parameter<ParamType> parameter, HttpRequest request)
        {
        if (canBind(parameter))
            {
            if (ParamType body := request.attributes.getAttribute(HttpAttributes.BODY))
                {
                return new BindingResult<ParamType>(body, True);
                }

            MediaType? mediaType = request.contentType;
            if (mediaType != Null, MediaTypeCodec codec := registry.findCodec(mediaType))
                {
                Object? requestBody = request.body;
                if (requestBody.is(Byte[]))
                    {
                    ParamType body = codec.decode<ParamType>(requestBody);
                    request.attributes.add(HttpAttributes.BODY, body);
                    return new BindingResult<ParamType>(body, True);
                    }
                }
            }
        return new BindingResult();
        }
    }