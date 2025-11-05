# Datascript Demo

Toy app to see how well UIX and Datascript would play together. Works suprisingly well.

## Findings:
To get UIX components to reload on query changes, I added a react hook listener to any changes on datascript DB atom.
I could probably listen for changes to attributes specific to the query to get better performance.
I wish I had "reactive" queries. 

Keeping app singletons like "app/selected-department" isn't straightforward, though totally possible. Slight impedence mismatch with ui/app state vars.

Now I appreciate Om Next / Fulcro's design, especially the co-located query fragments.
