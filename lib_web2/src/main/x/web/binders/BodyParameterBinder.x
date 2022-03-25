import ecstasy.reflect.Parameter;

import codecs.MediaTypeCodec;
import codecs.MediaTypeCodecRegistry;

import web.Body;

/**
 * A ParameterBinder that binds an HTTP request request body to a parameter.
 */
const BodyParameterBinder(MediaTypeCodecRegistry registry)
        implements ParameterBinder<Request>
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
    <ParamType> BindingResult<ParamType> bind(Parameter<ParamType> parameter, Request request)
        {
        if (canBind(parameter))
            {
            TODO if (ParamType body := request.attributes.getAttribute(HttpAttributes.BODY))
                {
                return new BindingResult<ParamType>(body, True);
                }

            MediaType? mediaType = request.contentType;
            if (mediaType != Null, MediaTypeCodec codec := registry.findCodec(mediaType))
                {
                Object? requestBody = request.body;
                if (requestBody.is(Byte[]))
                    {
                    assert Type[] paramType := &parameter.actualType.parameterized();
                    ParamType body = codec.decode<ParamType>(paramType[0], requestBody);
                    request.attributes.add(HttpAttributes.BODY, body);
                    return new BindingResult<ParamType>(body, True);
                    }
                }
            }
        return new BindingResult();
        }
    }